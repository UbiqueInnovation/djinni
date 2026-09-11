import Foundation
import DjinniSupport
import DjinniSupportCxx
import Smoke
import SmokeCxx

private struct AsyncImplementationError: Error, CustomStringConvertible {
    var description: String { "native Swift async failure" }
}

private final class FutureSource: Smoke.FutureCallback {
    let future: DJFuture<Smoke.Coordinate>
    var value: Smoke.Coordinate { get async throws { try await future.value } }
    var data: Data { get async throws { Data([4, 5, 6]) } }
    init(_ value: DJFuture<Smoke.Coordinate>) { future = value }
    func transformValues(_ values: [Smoke.Coordinate]) async throws -> [Smoke.Coordinate] {
        await Task.yield()
        return values
    }
    func transform(_ value: Smoke.Payload) async throws -> Smoke.Payload {
        await Task.yield()
        if value.note == "fail" { throw DjinniError("async callback failure") }
        if value.note == "swift-error" { throw AsyncImplementationError() }
        return value
    }
}

private final class ReleaseProbe {
    let released: () -> Void
    init(_ released: @escaping () -> Void) { self.released = released }
    deinit { released() }
}

private func checkSubscriptionLifetime() {
    // Dropping a bridge before attaching a subscription must not dereference nil.
    withExtendedLifetime(djinni.swift.makeFutureValue(cleanupCb)) {}
    let producer = DJPromise<Int>()
    let future = producer.getFuture()
    var didRelease = false
    var probe: ReleaseProbe? = ReleaseProbe {
        // Cancellation must release captures outside the subscription lock.
        let nested = future.getResult { _ in }
        nested.cancel()
        didRelease = true
    }
    let subscription = future.getResult { [probe = probe!] _ in withExtendedLifetime(probe) {} }
    probe = nil
    subscription.cancel()
    subscription.cancel()
    precondition(didRelease)
    producer.setValue(7)
    // Already-resolved callbacks must also support reentrancy.
    let ready = future.getResult { result in
        precondition(try! result.get() == 7)
        let nested = future.getResult { precondition(try! $0.get() == 7) }
        nested.cancel()
    }
    ready.cancel()

    let queue = DispatchQueue(label: "future-completion")
    let key = DispatchSpecificKey<Bool>()
    queue.setSpecific(key: key, value: true)
    let inline = DJPromise<Int>()
    let token = inline.getFuture().getResult { _ in
        precondition(DispatchQueue.getSpecific(key: key) == true, "Completion introduced a queue hop")
    }
    queue.sync { inline.setValue(1) }
    token.cancel()
}

private func checkAsyncFutures() async throws {
    let expected = Smoke.Coordinate(x: 7, y: 9)
    let first = try await Smoke.Engine.future(0)
    let second = try await Smoke.Engine.future(0)
    let ready = DJFuture<Smoke.Coordinate> { $0(.success(first)) }
    precondition(first == expected && second == expected)
    let delayed = try await Smoke.Engine.future(4)
    precondition(delayed == expected)
    for (mode, message) in [(Int32(1), "native future failure"), (2, "Unknown C++ exception"),
                            (3, "djinni::Promise was destructed before setting a result"), (5, "synchronous future failure")] {
        do {
            _ = try await Smoke.Engine.future(mode)
            fatalError("C++ future exception was lost")
        } catch let error as DjinniError {
            precondition(error.errorMessage == message)
        }
    }
    let engine = try Smoke.Engine.create()
    let payload = Smoke.Payload(items: [expected], note: String(repeating: "async", count: 1024))
    let values = Array(repeating: expected, count: 1024)
    let nativeValues = try await Smoke.Engine.futureValues(1024)
    precondition(nativeValues == values)
    let returnedValues = try await engine.runAsyncValues(values, callback: FutureSource(ready))
    precondition(returnedValues == values)
    let emptyValues = try await engine.runAsyncValues([], callback: FutureSource(ready))
    precondition(emptyValues.isEmpty)
    let asyncPayload = try await engine.runAsyncPayload(payload, callback: FutureSource(ready))
    precondition(asyncPayload == payload)
    for (note, message) in [("fail", "async callback failure"), ("swift-error", "native Swift async failure")] {
        do {
            _ = try await engine.runAsyncPayload(Smoke.Payload(items: [], note: note), callback: FutureSource(ready))
            fatalError("Async Swift implementation did not propagate its error")
        } catch let error as DjinniError {
            precondition(error.errorMessage == message)
        }
    }
    let firstData = try await Smoke.Engine.futureData()
    let secondData = try await Smoke.Engine.futureData()
    let callbackData = try await engine.runFutureData(FutureSource(ready))
    precondition(firstData == Data([4, 5, 6]) && firstData == secondData && callbackData == firstData)
    for failed in [false, true] {
        let producer = DJPromise<Smoke.Coordinate>()
        let source = FutureSource(producer.getFuture())
        let result = Task { try await engine.runFuture(source) }
        let completion = Task {
            if failed { producer.setException(DjinniError("Swift future failure")) }
            else { producer.setValue(Smoke.Coordinate(x: 7, y: 9)) }
        }
        do {
            let value = try await result.value
            precondition(!failed && value == expected)
        } catch let error as DjinniError {
            precondition(failed && error.errorMessage == "Swift future failure")
        }
        await completion.value
    }
    var abandoned: DJPromise<Smoke.Coordinate>? = DJPromise()
    let abandonedSource = FutureSource(abandoned!.getFuture())
    let abandonedNative = Task { try await engine.runFuture(abandonedSource) }
    abandoned = nil
    do {
        _ = try await abandonedNative.value
        fatalError("Abandoned Swift promise never failed")
    } catch let error as DjinniError {
        precondition(error.errorMessage == "DJPromise was deallocated before setting a result")
    }
    // Cancellation before or during subscription must finish, without resolving
    // or cancelling the shared producer and its other waiters.
    for index in 0..<100 {
        let producer = DJPromise<Int>()
        let future = producer.getFuture()
        let cancelled = Task { try await future.value }
        if index % 2 == 0 { await Task.yield() }
        cancelled.cancel()
        do {
            _ = try await cancelled.value
            fatalError("Cancelled await returned a value")
        } catch is CancellationError {}
        let survivor = Task { try await future.value }
        producer.setValue(index)
        let value = try await survivor.value
        precondition(value == index)
    }
    // Exercise cancellation through both typed bridge directions, with an
    // unresolved producer so completion cannot accidentally win this check.
    for _ in 0..<100 {
        let producer = DJPromise<Smoke.Coordinate>()
        let source = FutureSource(producer.getFuture())
        let cancelled = Task { try await engine.runFuture(source) }
        await Task.yield()
        cancelled.cancel()
        do {
            _ = try await cancelled.value
            fatalError("Cancelled native future returned a value")
        } catch is CancellationError {}
        producer.setValue(expected)
    }
    // Race resolution with cancellation: exactly one result reaches the awaiter.
    for index in 0..<100 {
        let producer = DJPromise<Smoke.Coordinate>()
        let source = FutureSource(producer.getFuture())
        let pending = Task { try await engine.runFuture(source) }
        let completion = Task { producer.setValue(Smoke.Coordinate(x: Double(index), y: 9)) }
        pending.cancel()
        do {
            let value = try await pending.value
            precondition(value == Smoke.Coordinate(x: Double(index), y: 9))
        } catch is CancellationError {}
        await completion.value
    }
}

private func checkTypedCompletionCleanup() {
    for ready in [false, true] {
        var promise: djinni_generated.Engine_futureReturnPromise? = .init()
        let future = promise!.getFuture()
        if ready { promise!.setValue(CoordinateMarshaller.toNative(Smoke.Coordinate(x: 7, y: 9))) }
        let events = UnsafeMutablePointer<Int>.allocate(capacity: 1)
        events.initialize(to: 0)
        future.subscribe(UnsafeMutableRawPointer(events), { context, raw in
            let result = raw!.assumingMemoryBound(to: djinni_generated.Engine_futureReturnResult.self)
            let events = context!.assumingMemoryBound(to: Int.self)
            events.pointee += result.pointee.hasError() ? 2 : 1
        }, { context in
            context!.assumingMemoryBound(to: Int.self).pointee += 10
        })
        // A pending C++ promise must report broken-promise and release context.
        promise = nil
        precondition(events.pointee == (ready ? 11 : 12))
        events.deinitialize(count: 1)
        events.deallocate()
    }
}

private func benchmarkNativeFutures() async throws {
    let expected = Array(repeating: Smoke.Coordinate(x: 7, y: 9), count: 1024)
    let iterations = 1000
    var legacyTimes: [Double] = []
    var nativeTimes: [Double] = []
    for round in 0..<6 {
        for native in (round % 2 == 0 ? [false, true] : [true, false]) {
            var value: [Smoke.Coordinate] = []
            let start = DispatchTime.now().uptimeNanoseconds
            for _ in 0..<iterations {
                if native { value = try await Smoke.Engine.futureValues(1024) }
                else {
                    value = try await FutureMarshaller<ListMarshaller<CoordinateMarshaller>>.fromCpp(SmokeCxx.legacyFutureValues(1024)).value
                }
            }
            let elapsed = Double(DispatchTime.now().uptimeNanoseconds - start) / Double(iterations)
            precondition(value == expected)
            if native { nativeTimes.append(elapsed) } else { legacyTimes.append(elapsed) }
        }
    }
    let old = legacyTimes.dropFirst().sorted()[2]
    let new = nativeTimes.dropFirst().sorted()[2]
    print(String(format: "1024-coordinate async result release benchmark: legacy %.1f ns, native %.1f ns, %.2fx", old, new, old / new))
}

func runFutureChecks() {
    let finished = DispatchSemaphore(value: 0)
    // This synchronous CLI check blocks its caller; run the async checks on the
    // cooperative executor rather than inheriting that caller's main executor.
    Task.detached {
        checkSubscriptionLifetime()
        checkTypedCompletionCleanup()
        do {
            try await checkAsyncFutures()
            if CommandLine.arguments.contains("--benchmark") { try await benchmarkNativeFutures() }
        }
        catch { fatalError("Future check failed: \(error)") }
        finished.signal()
    }
    precondition(finished.wait(timeout: .now() + 15) == .success, "Future callback or cancellation hung")
    print("Future errors, reentrancy, inline completion, cancellation races, and lifetime passed")
}
