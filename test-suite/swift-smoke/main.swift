import Smoke
import DjinniSupport
import Foundation

// Parcelable enables synthesized Codable, including nested records and optionals.
for value in [Payload(items: [Coordinate(x: 1.25, y: -2)], note: "round trip"), Payload(items: [], note: nil)] {
    let encoded = try JSONEncoder().encode(value)
    let decoded = try JSONDecoder().decode(Payload.self, from: encoded)
    precondition(decoded == value)
}
let emptyJSON = try JSONEncoder().encode(EmptyValue())
precondition(String(decoding: emptyJSON, as: UTF8.self) == "{}")
let emptyDecoded = try JSONDecoder().decode(EmptyValue.self, from: emptyJSON)
precondition(emptyDecoded == EmptyValue())
print("Parcelable records round-trip through Swift Codable")

// Checked conformances must be visible to clients of the generated module.
func requireSendable<T: Sendable>(_ type: T.Type) {}
requireSendable(Coordinate.self)
requireSendable(Payload.self)
requireSendable(Tagged.self)
requireSendable(Mode.self)
requireSendable(Options.self)

final class Transform: Callback {
    func transform(_ value: Coordinate) throws -> Coordinate {
        Coordinate(x: value.x + 1, y: value.y + 2)
    }
}
precondition(Mode.firstValue.rawValue == 2 && Mode.default.rawValue == 3)
let counter = try CounterInterface.create(initialValue: 37)
let counterValue = try counter.value
precondition(counterValue == 37)
let engine = try Engine.create()
try runNativeValueChecks(engine)
let defaultValue = try engine.default
precondition(defaultValue == 42)
try engine.setCount(9)
let count = try engine.count
precondition(count == 9)
try engine.setCount(-1)
do {
    _ = try engine.count
    fatalError("Throwing property lost native exception")
} catch {
    precondition((error as? DjinniError)?.errorMessage == "invalid count")
}
final class MutableState: State {
    var stored: Int32 = 10
    var value: Int32 { get throws { stored } }
    func setValue(_ value: Int32) throws { stored = value }
}
let swiftState = MutableState()
let updated = try engine.updateState(swiftState)
precondition(updated == 11 && swiftState.stored == 11)
let nativeState = try Engine.state()
try nativeState.setValue(20)
let nativeValue = try nativeState.value
precondition(nativeValue == 20)
final class Rich: RichCallback {
    var notifications = 0
    func notify() throws { notifications += 1 }
    func inspect(_ value: Payload) throws -> Payload { value }
}
let rich = Rich()
let richPayload = Payload(items: [Coordinate(x: 7, y: 9)], note: "callback 👋")
let richResult = try engine.inspect(richPayload, callback: rich)
precondition(richResult == richPayload && rich.notifications == 1)
final class Reentrant: Callback {
    func transform(_ value: Coordinate) throws -> Coordinate { try engine.shift(value) }
}
let reentrant = try engine.run(Coordinate(x: 4, y: 5), callback: Reentrant())
precondition(reentrant == Coordinate(x: 5, y: 7))
final class Throwing: Callback {
    func transform(_ value: Coordinate) throws -> Coordinate { throw DjinniError("callback failed") }
}
do {
    _ = try engine.run(Coordinate(x: 1, y: 2), callback: Throwing())
    fatalError("Swift callback exception did not round trip")
} catch {
    precondition((error as? DjinniError)?.errorMessage == "callback failed")
}
weak var retainedCallback: Transform?
do {
    let callback = Transform()
    retainedCallback = callback
    try engine.retain(callback)
}
precondition(retainedCallback != nil)
let retainedResult = try engine.retained(Coordinate(x: 2, y: 3))
precondition(retainedResult == Coordinate(x: 3, y: 5))
try engine.release()
precondition(retainedCallback == nil, "Native callback retention leaked Swift object")
let tagged = Tagged(value: Coordinate(x: 2, y: 3), mode: .second, options: [.read, .write])
let taggedResult = try engine.tag(tagged)
precondition(taggedResult == tagged)
for text in ["", "Grüezi 👋\0world", String(repeating: "abc", count: 1000)] {
    let result = try engine.text(text)
    precondition(result == text)
}
for note: String? in [nil, "optional 👋"] {
    let payload = Payload(items: [Coordinate(x: 1, y: 2), Coordinate(x: 3, y: 4)], note: note)
    let result = try engine.payload(payload)
    precondition(result == payload)
}
let shifted = try engine.shift(Coordinate(x: 8, y: 47))
precondition(shifted == Coordinate(x: 9, y: 49))
let scaled = try Engine.scale(3)
precondition(scaled == 6)
let value = try engine.run(Coordinate(x: 8, y: 47), callback: Transform())
precondition(value == Coordinate(x: 9, y: 49))
do {
    try engine.fail()
    fatalError("C++ exception was not translated")
} catch {
    precondition((error as? DjinniError)?.errorMessage == "native failure")
}
for (input, message) in [(Coordinate(x: -1, y: 0), "invalid coordinate"),
                         (Coordinate(x: 0, y: -1), "Unknown C++ exception")] {
    do {
        _ = try engine.shift(input)
        fatalError("Typed result did not translate C++ exception")
    } catch {
        precondition((error as? DjinniError)?.errorMessage == message)
    }
}
print("Swift/C++ typed records, static calls, callbacks and exception checks passed")

weak var releasedData: NSData?
autoreleasepool {
    let data = NSMutableData(data: Data([1, 2, 3]))
    releasedData = data
    let echoed = try! engine.echo(data as Data)
    precondition((echoed as Data) == Data([1, 2, 3]))
}
precondition(releasedData == nil, "Swift-to-C++ data transfer leaked its retained input")
let bytes = try engine.bytes()
precondition(bytes as Data == Data([4, 5, 6]))
print("DataRef round-trip and ownership checks passed")

let producer = DJPromise<Int>()
let completed = DispatchSemaphore(value: 0)
let subscription = producer.getFuture().getResult { result in
    precondition((try? result.get()) == 7)
    completed.signal()
}
producer.setValue(7)
precondition(completed.wait(timeout: .now() + 5) == .success)
withExtendedLifetime(subscription) {}
let failedProducer = DJPromise<Int>()
let failed = DispatchSemaphore(value: 0)
let failureSubscription = failedProducer.getFuture().getResult { result in
    guard case .failure(let error) = result else { fatalError("Expected failed future") }
    precondition(error.errorMessage == "Swift failure")
    failed.signal()
}
failedProducer.setException(DjinniError("Swift failure"))
precondition(failed.wait(timeout: .now() + 5) == .success)
withExtendedLifetime(failureSubscription) {}
print("Swift promise success and failure checks passed")

if CommandLine.arguments.contains("--benchmark") { try benchmark(engine)
    try benchmarkPayload(engine) }

try checkProxyCaches()

for present in [false, true] {
    let input = Containers(
        nested: [[], [Coordinate(x: 1, y: 2)], Array(repeating: Coordinate(x: 3, y: 4), count: 1024)],
        flags: [true, false, true],
        optionalItems: present ? [] : nil,
        notes: [nil, "", "Grüezi 👋\0"],
        tagged: present ? tagged : nil,
        values: [0, -1.5, Double.greatestFiniteMagnitude])
    let output = try engine.containers(input)
    precondition(output == input)
}
print("Native nested lists, optional containers and mixed element types passed")

final class ContainerTransform: ContainerCallback {
    func transform(_ values: [Coordinate], names: [String?]?, flags: [Bool]) throws -> [Coordinate]? {
        precondition(flags == [true, false])
        guard let names else { return nil }
        if names == ["throw"] { throw DjinniError("container callback failed") }
        return values.map { Coordinate(x: $0.x + 1, y: $0.y + 2) }
    }
}
let points = (0..<1024).map { Coordinate(x: Double($0), y: Double($0)) }
for input in [[], points] {
    let output = try engine.points(input)
    precondition(output == input)
    for names: [String?]? in [nil, [], [nil, "Grüezi 👋\0"]] {
        let output = try engine.runContainers(input, names: names, flags: [true, false], callback: ContainerTransform())
        let expected = names.map { _ in input.map { Coordinate(x: $0.x + 1, y: $0.y + 2) } }
        precondition(output == expected)
    }
}
for input: [Coordinate]? in [nil, [], points] {
    let output = try engine.optionalPoints(input)
    precondition(output == input)
}
for input: Int32? in [nil, 0, Int32.min, Int32.max] {
    let output = try Engine.optionalNumber(input)
    precondition(output == input)
}
do {
    _ = try engine.points([Coordinate(x: -1, y: 0)])
    fatalError("Native collection exception was lost")
} catch { precondition((error as? DjinniError)?.errorMessage == "invalid points") }
do {
    _ = try engine.runContainers([], names: ["throw"], flags: [true, false], callback: ContainerTransform())
    fatalError("Swift collection callback exception was lost")
} catch { precondition((error as? DjinniError)?.errorMessage == "container callback failed") }
print("Native collection arguments, optional results and bidirectional callbacks passed")

let staticCollection = try Engine.containerOnly([ContainerOnly(number: 4)])
precondition(staticCollection == [ContainerOnly(number: 4)])

final class MapTransform: MapCallback {
    func transform(_ values: [String: Coordinate], tags: Set<String>) throws -> [String: Coordinate] {
        precondition(tags == ["one", "Grüezi 👋\0"])
        return values.mapValues { Coordinate(x: $0.x + 1, y: $0.y + 2) }
    }
}
for values: [String: Coordinate] in [[:], ["": Coordinate(x: 1, y: 2), "Grüezi 👋\0": Coordinate(x: 3, y: 4)]] {
    let result = try engine.locations(values)
    precondition(result == values)
    let tags: Set<String> = ["one", "Grüezi 👋\0"]
    let resultTags = try engine.tags(tags)
    precondition(resultTags == tags)
    let callbackResult = try engine.runMap(values, tags: tags, callback: MapTransform())
    precondition(callbackResult == values.mapValues { Coordinate(x: $0.x + 1, y: $0.y + 2) })
}
for present in [false, true] {
    var locations: [String: Coordinate?] = [:]
    locations.updateValue(nil, forKey: "nil")
    locations.updateValue(Coordinate(x: 2, y: 3), forKey: "value")
    let input = Associative(locations: locations, tags: [], groups: ["empty": [], "points": points],
        nested: present ? ["integers": [Int32.min, 0, Int32.max]] : nil,
        flags: [true: [true, false], false: []], modes: [.first, .second])
    let result = try engine.associative(input)
    precondition(result == input && result.locations.keys.contains("nil"))
}
print("Native maps, sets, optional map values and callbacks passed")

// Native shared-pointer containers preserve identity, null entries, and ownership.
final class InterfaceTransform: InterfaceCallback {
    func transform(_ values: [State], fallback: State?) throws -> [State] {
        values + (fallback.map { [$0] } ?? [])
    }
}
let interfaceValues = InterfaceValues(states: [swiftState, nativeState, swiftState], named: ["swift": swiftState, "native": nativeState, "nil": nil], counter: counter)
let interfaceResult = try engine.interfaces(interfaceValues)
precondition(interfaceResult.states[0] === swiftState && interfaceResult.states[1] === nativeState)
precondition(interfaceResult.states[2] === swiftState && interfaceResult.counter === counter)
precondition(interfaceResult.named.count == 3 && interfaceResult.named["nil"] != nil && interfaceResult.named["nil"]! == nil)
precondition(interfaceResult.named["swift"]! === swiftState && interfaceResult.named["native"]! === nativeState)
let emptyInterfaces = try engine.interfaces(InterfaceValues(states: [], named: [:], counter: nil))
precondition(emptyInterfaces.states.isEmpty && emptyInterfaces.named.isEmpty && emptyInterfaces.counter == nil)
let interfaceCallback = InterfaceTransform()
let callbackInterfaces = try engine.runInterfaces([swiftState, nativeState], fallback: swiftState, callback: interfaceCallback)
precondition(callbackInterfaces.count == 3 && callbackInterfaces[0] === swiftState && callbackInterfaces[1] === nativeState && callbackInterfaces[2] === swiftState)
let emptyCallbackInterfaces = try engine.runInterfaces([], fallback: nil, callback: interfaceCallback)
precondition(emptyCallbackInterfaces.isEmpty)
weak var collectionOwnedState: MutableState?
do {
    var retained: InterfaceValues?
    do {
        let state = MutableState()
        collectionOwnedState = state
        retained = try engine.interfaces(InterfaceValues(states: [state], named: [:], counter: nil))
    }
    precondition(collectionOwnedState != nil)
    withExtendedLifetime(retained) {}
    retained = nil
}
precondition(collectionOwnedState == nil)
print("Native interface collections preserve identity, optional values, callbacks, and lifetime")

final class DataTransform: DataCallback {
    func transform(_ value: DataValues) throws -> DataValues { value }
}
let largeData = Data((0..<65536).map { UInt8(truncatingIfNeeded: $0) })
for input in [Data(), Data([7]), largeData] {
    let echoed = try engine.echo(input)
    let binary = try engine.binary(input)
    precondition(echoed == input && binary == input)
    for optional in [nil, Data(), input] as [Data?] {
        let value = DataValues(binary: input, optionalBinary: optional, binaries: [Data(), input], data: input, optionalData: optional, chunks: [Data(), input])
        let result = try engine.runData(value, callback: DataTransform())
        precondition(result.data == input && result.optionalData == optional && result.chunks == value.chunks)
        precondition(result.binary == input && result.optionalBinary == optional && result.binaries == value.binaries)
    }
}
var mutableData = largeData
let nativeData = DataRefMarshaller.toNative(mutableData)
mutableData[0] = 99
precondition(DataRefMarshaller.fromNative(nativeData) == largeData)
let nativeDataCopy = nativeData
precondition(DataRefMarshaller.fromNative(nativeDataCopy) == largeData)
let survivingData: Data = {
    let temporary = DataRefMarshaller.toNative(largeData)
    return DataRefMarshaller.fromNative(temporary)
}()
precondition(survivingData == largeData)
print("Native DataRef and binary preserve empty buffers, optional values, callbacks, copies, and lifetime")

runFutureChecks()

for milliseconds: Int64 in [-1001, 0, 1, 1_750_000_000_123] {
    let date = Date(timeIntervalSince1970: Double(milliseconds) / 1000)
    precondition(I64Marshaller.fromCpp(DateMarshaller.toCpp(date)) == milliseconds)
    let restoredDate = DateMarshaller.fromCpp(I64Marshaller.toCpp(milliseconds))
    precondition(abs(restoredDate.timeIntervalSince1970 - date.timeIntervalSince1970) < 0.000001)
}
print("Date marshalling preserves C++ epoch milliseconds before and after 1970")
