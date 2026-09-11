import Foundation
import Smoke
import DjinniSupport
import DjinniSupportCxx

private final class BothProtocols: Smoke.Callback, Smoke.State {
    var value: Int32 { get throws { 3 } }
    func setValue(_ value: Int32) throws {}
    func transform(_ value: Smoke.Coordinate) throws -> Smoke.Coordinate { value }
}

func checkProxyCaches() throws {
    let both = BothProtocols()
    let callback = CallbackMarshaller.toCpp(both as Smoke.Callback)
    let state = StateMarshaller.toCpp(both as Smoke.State)
    let callbackContext = withUnsafePointer(to: callback) { djinni.swift.getInterfaceInfo($0).ctxPointer }
    let stateContext = withUnsafePointer(to: state) { djinni.swift.getInterfaceInfo($0).ctxPointer }
    precondition(callbackContext != stateContext, "Distinct protocols reused the same native proxy")
    precondition(CallbackMarshaller.fromCpp(callback) === both)
    precondition(StateMarshaller.fromCpp(state) === both)

    let expiredWeak = { () -> djinni.swift.WeakSwiftProxy in
        let temporary = CallbackMarshaller.toCpp(Transform() as Smoke.Callback)
        return djinni.swift.weakify(temporary)
    }()
    var expired = djinni.swift.strongify(expiredWeak)
    precondition(djinni.swift.isVoidValue(&expired), "Expired native proxy did not return an empty result")

    let engine = try Smoke.Engine.create()
    let updated = try engine.updateState(both)
    precondition(updated == 3)
    let point = Smoke.Coordinate(x: 4, y: 5)
    let result = try engine.run(point, callback: both)
    precondition(result == point)

    // No Swift wrapper remains alive between iterations. Hold only the native object.
    let native = CounterInterfaceMarshaller.toCpp(try Smoke.CounterInterface.create(initialValue: 37))
    DispatchQueue.concurrentPerform(iterations: 8) { _ in
        for _ in 0..<1_000 {
            autoreleasepool {
                let first = CounterInterfaceMarshaller.fromCpp(native)
                let second = CounterInterfaceMarshaller.fromCpp(native)
                precondition(first === second, "Concurrent conversions duplicated a live Swift proxy")
                let value = try! first.value
                precondition(value == 37)
                // Exercise short-lived callbacks alongside native wrapper destruction.
                let callback = Transform()
                let shifted = try! engine.run(point, callback: callback)
                precondition(shifted == Smoke.Coordinate(x: 5, y: 7))
            }
        }
    }
    // Contend on the same callback key, including weak promotion during destruction.
    let sharedCallback = Transform()
    DispatchQueue.concurrentPerform(iterations: 8) { _ in
        for _ in 0..<1_000 {
            let shifted = try! engine.run(point, callback: sharedCallback)
            precondition(shifted == Smoke.Coordinate(x: 5, y: 7))
        }
    }
    print("Proxy identity, multiple protocols and concurrent lifetime checks passed")
}
