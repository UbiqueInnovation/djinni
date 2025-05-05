import Smoke
import DjinniSupport
import Foundation

final class Transform: Callback {
    func transform(_ value: Coordinate) throws -> Coordinate {
        Coordinate(x: value.x + 1, y: value.y + 2)
    }
}
let engine = try Engine_statics.create()
let value = try engine.run(Coordinate(x: 8, y: 47), callback: Transform())
precondition(value == Coordinate(x: 9, y: 49))
do {
    try engine.fail()
    fatalError("C++ exception was not translated")
} catch {
    precondition((error as? DjinniError)?.errorMessage == "native failure")
}
print("Swift/C++ record, virtual method, callback and exception checks passed")

weak var releasedData: NSData?
autoreleasepool {
    let data = NSMutableData(data: Data([1, 2, 3]))
    releasedData = data
    let echoed = try! engine.echo(data)
    precondition(echoed === data)
    precondition((echoed as Data) == Data([1, 2, 3]))
}
precondition(releasedData == nil, "Swift-to-C++ data transfer leaked its retained input")
let bytes = try engine.bytes()
precondition(bytes as Data == Data([4, 5, 6]))
print("DataRef round-trip and ownership checks passed")
