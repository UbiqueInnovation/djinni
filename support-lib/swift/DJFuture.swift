import DjinniSupportCxx
import Foundation

public final class Cancellable {
    private let lock = NSLock()
    private var closure: (() -> Void)?

    init(closure: @escaping () -> Void) { self.closure = closure }

    public func cancel() {
        lock.lock()
        let action = closure
        closure = nil
        lock.unlock()
        action?()
    }

    deinit { cancel() }
}

public typealias Lock = NSLock

public final class Future<Output, Failure> where Failure: Error {
    public typealias Promise = (Result<Output, Failure>) -> Void
    private typealias Token = Int64
    private var nextToken: Token = 0
    // C++ completion and subscription are synchronous and may run on different threads.
    // This lock protects state only; user callbacks always run after unlocking.
    private let lock = NSLock()
    private var storedResult: Result<Output, Failure>?
    private var subscriptions = [Token: Promise]()

    public init(_ attemptToFulfill: @escaping (@escaping Promise) -> Void) {
        attemptToFulfill { [weak self] result in
            guard let self else { return }
            let completed = self.resolve(result)
            assert(completed, "attempted to fulfill future multiple times")
        }
    }

    /// Starts an async operation from a synchronous bridge, on the nonisolated executor.
    public convenience init(operation: @escaping () async throws -> Output) where Failure == DjinniError {
        self.init { complete in
            Task {
                do { complete(.success(try await operation())) }
                catch let error as DjinniError { complete(.failure(error)) }
                catch { complete(.failure(DjinniError(String(describing: error)))) }
            }
        }
    }

    @discardableResult
    func resolve(_ makeResult: @autoclosure () -> Result<Output, Failure>) -> Bool {
        lock.lock()
        guard storedResult == nil else {
            lock.unlock()
            return false
        }
        let result = makeResult()
        let callbacks = subscriptions.values
        subscriptions = [:]
        storedResult = result
        lock.unlock()
        // Match the C++ future and the already-resolved path: no forced queue hop.
        for callback in callbacks { callback(result) }
        return true
    }

    public var value: Output {
        get async throws {
            try Task.checkCancellation()
            // AsyncThrowingStream owns the completion/cancellation race and unregisters
            // the subscription when its single result is consumed or the task is cancelled.
            let stream = AsyncThrowingStream<Output, Error> { continuation in
                let subscription = getResult { result in
                    switch result {
                    case .success(let value):
                        continuation.yield(value)
                        continuation.finish()
                    case .failure(let error):
                        continuation.finish(throwing: error)
                    }
                }
                continuation.onTermination = { _ in subscription.cancel() }
            }
            var iterator = stream.makeAsyncIterator()
            guard let value = try await iterator.next() else { throw CancellationError() }
            return value
        }
    }

    /// Invokes the callback on the resolving thread, or immediately if already resolved.
    public func getResult(subscription: @escaping Promise) -> Cancellable {
        lock.lock()
        nextToken &+= 1
        let token = nextToken
        let result = storedResult
        if result == nil { subscriptions[token] = subscription }
        lock.unlock()
        if let result { subscription(result) }
        return Cancellable { self.cancel(token: token) }
    }

    private func cancel(token: Token) {
        lock.lock()
        // Release captured objects after unlocking: their deinit may reenter this future.
        let removed = subscriptions.removeValue(forKey: token)
        lock.unlock()
        withExtendedLifetime(removed) {}
    }
}

public typealias DJFuture<T> = Future<T, DjinniError>

// Type erased interface for PromiseHolder because in futureCb() we don't have
// the type parameter.
protocol AbstractPromiseHolder: AnyObject {
    func fulfillPromise(value: UnsafePointer<djinni.swift.AnyValue>)
}
// The Swift Future wrapper object that can be fulfilled by a C++ call
class PromiseHolder<T: Marshaller>: AbstractPromiseHolder {
    var promise: DJFuture<T.SwiftType>.Promise
    init(marshaller: T.Type, promise: @escaping DJFuture<T.SwiftType>.Promise) {
        self.promise = promise
    }
    func fulfillPromise(value: UnsafePointer<djinni.swift.AnyValue>) {
        if (!djinni.swift.isError(value)) {
            promise(.success(T.fromCpp(value.pointee)))
        } else {
            promise(.failure(DjinniError(djinni.swift.getError(value))))
        }
    }
}
// A C++ friendly function. This is passed to C++ as the continuation routine of
// the C++ future. It calls the PromiseHolder and forwards the C++ future's
// result or error to the Swift future.
public func futureCb(
  ptr: UnsafeMutableRawPointer?,
  result: UnsafeMutablePointer<djinni.swift.AnyValue>?)
  -> Void {
    let ctx = Unmanaged<AnyObject>.fromOpaque(ptr!).takeRetainedValue()
    let promiseHolder = ctx as! AbstractPromiseHolder
    promiseHolder.fulfillPromise(value:result!)
}

// A C++ friendly function to release the subscription token stored with the C++
// future value.
public func cleanupCb(psubscription: UnsafeMutableRawPointer?) -> Void {
    guard let psubscription else { return }
    _ = Unmanaged<Cancellable>.fromOpaque(psubscription).takeRetainedValue()
}

public enum FutureMarshaller<T: Marshaller>: Marshaller {
    public typealias SwiftType = DJFuture<T.SwiftType>
    // A C++ callback must return its future synchronously. The task owns the
    // producer until the Swift async implementation completes on its executor.
    public static func toCppAsync(_ operation: @escaping () async throws -> T.SwiftType) -> djinni.swift.AnyValue {
        return toCpp(SwiftType(operation: operation))
    }
    public static func fromCpp(_ v: djinni.swift.AnyValue) -> SwiftType {
        return Future() { promise in
            // Allocate the Swift future wrapper
            let promiseHolder = PromiseHolder(marshaller: T.self, promise: promise)
            let promiseHolderPtr = Unmanaged.passRetained(promiseHolder).toOpaque()
            // And connect it with the C++ future
            withUnsafePointer(to: v) { p in
                djinni.swift.setFutureCb(p, futureCb, promiseHolderPtr)
            }
        }
    }
    public static func toCpp(_ s: SwiftType) -> djinni.swift.AnyValue {
        // Create a C++ future
        let futureValue = djinni.swift.makeFutureValue(cleanupCb)
        // Connect it with the Swift future
        let cancellable = s.getResult { result in
            switch result {
            case .success(let value):
                let cppValue = T.toCpp(value)
                withUnsafePointer(to: futureValue) { future in
                    withUnsafePointer(to: cppValue) { djinni.swift.setFutureResult(future, $0) }
                }
            case .failure(let error):
                var errorValue = djinni.swift.makeVoidValue()
                djinni.swift.setErrorValue(&errorValue, error.wrapped)
                withUnsafePointer(to: futureValue) { future in
                    withUnsafePointer(to: errorValue) { djinni.swift.setFutureResult(future, $0) }
                }
            }
        }
        // Store the cancellable token so that the connection remains alive.
        let pSubscription = Unmanaged.passRetained(cancellable).toOpaque()
        withUnsafePointer(to: futureValue) { djinni.swift.storeSubscription($0, pSubscription) }
        return futureValue
    }
}

// C++ owns this context until its completion handler is destroyed, including
// abandoned producers. Cancellation finishes the waiter without cancelling C++.
private final class NativeFutureCompletion {
    let complete: (UnsafeMutableRawPointer) -> Void
    init(_ complete: @escaping (UnsafeMutableRawPointer) -> Void) { self.complete = complete }
}

public func awaitNativeFuture<Value>(
    _ subscribe: (UnsafeMutableRawPointer, @convention(c) (UnsafeMutableRawPointer?, UnsafeMutableRawPointer?) -> Void, @convention(c) (UnsafeMutableRawPointer?) -> Void) -> Void,
    decode: @escaping (UnsafeMutableRawPointer) throws -> Value
) async throws -> Value {
    try Task.checkCancellation()
    let stream = AsyncThrowingStream<Value, Error> { continuation in
        let context = NativeFutureCompletion { raw in
            do {
                continuation.yield(try decode(raw))
                continuation.finish()
            } catch { continuation.finish(throwing: error) }
        }
        subscribe(Unmanaged.passRetained(context).toOpaque(), { context, result in
            Unmanaged<NativeFutureCompletion>.fromOpaque(context!).takeUnretainedValue().complete(result!)
        }, { context in
            Unmanaged<NativeFutureCompletion>.fromOpaque(context!).release()
        })
    }
    var iterator = stream.makeAsyncIterator()
    guard let value = try await iterator.next() else { throw CancellationError() }
    return value
}

public func nativeFutureError(_ error: Error) -> djinni.swift.ErrorValue {
    (error as? DjinniError ?? DjinniError(String(describing: error))).wrapped
}
