import Foundation
import Smoke
import SmokeCxx
import DjinniSupport
import DjinniSupportCxx

// Same call sequence emitted by the original generic generator.
@inline(never)
func legacyShift(_ instance: djinni.swift.AnyValue, _ coordinate: Smoke.Coordinate) throws -> Smoke.Coordinate {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(CoordinateMarshaller.toCpp(coordinate))
    var result = SmokeCxx.legacyShift(&params)
    try handleCppErrors(&result)
    return CoordinateMarshaller.fromCpp(result)
}

@inline(never)
func legacyRun(_ instance: djinni.swift.AnyValue, _ coordinate: Smoke.Coordinate, _ callback: Smoke.Callback) throws -> Smoke.Coordinate {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(CoordinateMarshaller.toCpp(coordinate))
    params.addValue(CallbackMarshaller.toCpp(callback))
    var result = SmokeCxx.legacyRun(&params)
    try handleCppErrors(&result)
    return CoordinateMarshaller.fromCpp(result)
}

@inline(never)
func typedRun(_ engine: Smoke.Engine, _ coordinate: Smoke.Coordinate, _ callback: Smoke.Callback) throws -> Smoke.Coordinate {
    try engine.run(coordinate, callback: callback)
}

@inline(never)
func typedShift(_ engine: Smoke.Engine, _ coordinate: Smoke.Coordinate) throws -> Smoke.Coordinate {
    try engine.shift(coordinate)
}

func benchmark(_ engine: Smoke.Engine) throws {
    let instance = EngineMarshaller.toCpp(engine)
    let count = 200_000
    var legacyTimes: [Double] = []
    var typedTimes: [Double] = []
    func measure(_ call: (Smoke.Coordinate) throws -> Smoke.Coordinate) rethrows -> Double {
        var value = Smoke.Coordinate(x: 0, y: 0)
        let start = DispatchTime.now().uptimeNanoseconds
        for _ in 0..<count { value = try call(value) }
        let elapsed = DispatchTime.now().uptimeNanoseconds - start
        precondition(value.x == Double(count) && value.y == Double(count * 2))
        return Double(elapsed) / Double(count)
    }
    // Alternate order to reduce warm-up/order bias. Compare medians, not one run.
    for round in 0..<6 {
        if round % 2 == 0 {
            legacyTimes.append(try measure { try legacyShift(instance, $0) })
            typedTimes.append(try measure { try typedShift(engine, $0) })
        } else {
            typedTimes.append(try measure { try typedShift(engine, $0) })
            legacyTimes.append(try measure { try legacyShift(instance, $0) })
        }
    }
    let legacy = legacyTimes.dropFirst().sorted()[2]
    let typed = typedTimes.dropFirst().sorted()[2]
    print(String(format: "Coordinate call release benchmark: legacy %.1f ns, typed %.1f ns, %.2fx", legacy, typed, legacy / typed))
    let callback = Transform()
    legacyTimes.removeAll(keepingCapacity: true)
    typedTimes.removeAll(keepingCapacity: true)
    for round in 0..<6 {
        if round % 2 == 0 {
            legacyTimes.append(try measure { try legacyRun(instance, $0, callback) })
            typedTimes.append(try measure { try typedRun(engine, $0, callback) })
        } else {
            typedTimes.append(try measure { try typedRun(engine, $0, callback) })
            legacyTimes.append(try measure { try legacyRun(instance, $0, callback) })
        }
    }
    let legacyCallback = legacyTimes.dropFirst().sorted()[2]
    let typedCallback = typedTimes.dropFirst().sorted()[2]
    print(String(format: "Callback round trip (typed reverse bridge in both): legacy forward %.1f ns, typed forward %.1f ns, %.2fx", legacyCallback, typedCallback, legacyCallback / typedCallback))

}

@inline(never)
private func legacyPayload(_ instance: djinni.swift.AnyValue, _ payload: Smoke.Payload) throws -> Smoke.Payload {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(PayloadMarshaller.toCpp(payload))
    var result = SmokeCxx.legacyPayload(&params)
    try handleCppErrors(&result)
    return PayloadMarshaller.fromCpp(result)
}

@inline(never)
private func legacyPoints(_ instance: djinni.swift.AnyValue, _ values: [Smoke.Coordinate]) throws -> [Smoke.Coordinate] {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(ListMarshaller<CoordinateMarshaller>.toCpp(values))
    var result = SmokeCxx.legacyPoints(&params)
    try handleCppErrors(&result)
    return ListMarshaller<CoordinateMarshaller>.fromCpp(result)
}

@inline(never)
private func legacyLocations(_ instance: djinni.swift.AnyValue, _ values: [String: Smoke.Coordinate]) throws -> [String: Smoke.Coordinate] {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(MapMarshaller<StringMarshaller, CoordinateMarshaller>.toCpp(values))
    var result = SmokeCxx.legacyLocations(&params)
    try handleCppErrors(&result)
    return MapMarshaller<StringMarshaller, CoordinateMarshaller>.fromCpp(result)
}

private func benchmarkContainer<T>(_ name: String, input: T, equal: (T, T) -> Bool,
    legacyCall: () throws -> T, nativeCall: () throws -> T) throws {
    func measure(_ call: () throws -> T) rethrows -> Double {
        var result = input
        let start = DispatchTime.now().uptimeNanoseconds
        for _ in 0..<1000 { result = try call() }
        let elapsed = DispatchTime.now().uptimeNanoseconds - start
        precondition(equal(result, input))
        return Double(elapsed) / 1000
    }
    var legacy: [Double] = []
    var native: [Double] = []
    for round in 0..<6 {
        if round % 2 == 0 {
            legacy.append(try measure(legacyCall))
            native.append(try measure(nativeCall))
        } else {
            native.append(try measure(nativeCall))
            legacy.append(try measure(legacyCall))
        }
    }
    let old = legacy.dropFirst().sorted()[2]
    let new = native.dropFirst().sorted()[2]
    print(String(format: "%@ release benchmark: legacy %.1f ns, native %.1f ns, %.2fx", name, old, new, old / new))
}

func benchmarkPayload(_ engine: Smoke.Engine) throws {
    let instance = EngineMarshaller.toCpp(engine)
    let input = Smoke.Payload(items: (0..<1024).map { Smoke.Coordinate(x: Double($0), y: Double($0 + 1)) }, note: "payload")
    try benchmarkContainer("1024-coordinate record", input: input, equal: ==,
        legacyCall: { try legacyPayload(instance, input) }, nativeCall: { try engine.payload(input) })
    try benchmarkContainer("1024-coordinate array", input: input.items, equal: ==,
        legacyCall: { try legacyPoints(instance, input.items) }, nativeCall: { try engine.points(input.items) })
    let sharedState = try Smoke.Engine.state()
    let interfaces = Smoke.InterfaceValues(states: Array(repeating: sharedState, count: 1024), named: [:], counter: nil)
    try benchmarkContainer("1024-interface record", input: interfaces,
        equal: { result, input in result.states.count == input.states.count && zip(result.states, input.states).allSatisfy { $0 === $1 } },
        legacyCall: { try legacyInterfaces(instance, interfaces) }, nativeCall: { try engine.interfaces(interfaces) })
    let data = Data((0..<65536).map { UInt8(truncatingIfNeeded: $0) })
    let nativeBytes = BinaryMarshaller.toNative(data)
    let legacyBytes = BinaryMarshaller.toCpp(data)
    try benchmarkContainer("64 KiB decode only", input: data, equal: ==,
        legacyCall: { BinaryMarshaller.fromCpp(legacyBytes) },
        nativeCall: { BinaryMarshaller.fromNative(nativeBytes) })
    try benchmarkContainer("64 KiB encode and decode", input: data, equal: ==,
        legacyCall: { BinaryMarshaller.fromCpp(BinaryMarshaller.toCpp(data)) },
        nativeCall: { BinaryMarshaller.fromNative(BinaryMarshaller.toNative(data)) })
    try benchmarkContainer("64 KiB binary", input: data, equal: ==,
        legacyCall: { try legacyBinary(instance, data) }, nativeCall: { try engine.binary(data) })
    try benchmarkContainer("64 KiB DataRef", input: data, equal: ==,
        legacyCall: { try legacyDataRef(instance, data) }, nativeCall: { try engine.echo(data) })
    let locations = Dictionary(uniqueKeysWithValues: input.items.enumerated().map { (String($0.offset), $0.element) })
    try benchmarkContainer("1024-entry coordinate dictionary", input: locations, equal: ==,
        legacyCall: { try legacyLocations(instance, locations) }, nativeCall: { try engine.locations(locations) })
}

@inline(never)
private func legacyInterfaces(_ instance: djinni.swift.AnyValue, _ values: Smoke.InterfaceValues) throws -> Smoke.InterfaceValues {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(InterfaceValuesMarshaller.toCpp(values))
    var result = SmokeCxx.legacyInterfaces(&params)
    try handleCppErrors(&result)
    return InterfaceValuesMarshaller.fromCpp(result)
}

@inline(never)
private func legacyBinary(_ instance: djinni.swift.AnyValue, _ data: Data) throws -> Data {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(BinaryMarshaller.toCpp(data))
    var result = SmokeCxx.legacyBinary(&params)
    try handleCppErrors(&result)
    return BinaryMarshaller.fromCpp(result)
}

@inline(never)
private func legacyDataRef(_ instance: djinni.swift.AnyValue, _ data: Data) throws -> Data {
    var params = djinni.swift.ParameterList()
    params.addValue(instance)
    params.addValue(DataRefMarshaller.toCpp(data))
    var result = SmokeCxx.legacyDataRef(&params)
    try handleCppErrors(&result)
    return DataRefMarshaller.fromCpp(result)
}
