import Foundation
import Smoke
import SmokeCxx
import DjinniSupport

func runNativeValueChecks(_ engine: Smoke.Engine) throws {
    let absent = try Smoke.Engine.optionalNumber(nil)
    precondition(absent == nil) // A nil literal must not match a second C++ optional overload.
    // Both spellings denote the same C++ type; no marshaller is called here.
    let coordinate: SmokeCxx.Coordinate = Smoke.Coordinate(x: 1, y: 2)
    let shifted: SmokeCxx.Coordinate = try engine.shift(coordinate)
    precondition(shifted.x == 2 && shifted.y == 4)
    var input = djinni_generated.Engine_pointsArg0()
    input.push_back(coordinate)
    let returned = try engine.points(input)
    precondition(returned.size() == 1 && returned[0] == coordinate)
    var copied = returned
    copied[0].x = 9
    precondition(returned[0].x == 1 && copied[0].x == 9)
    let empty = try engine.points(djinni_generated.Engine_pointsArg0())
    precondition(empty.empty())
    input[0].x = -1
    do {
        _ = try engine.points(input)
        preconditionFailure("Native container overload lost error translation")
    } catch {
        precondition((error as? DjinniError)?.errorMessage == "invalid points")
    }
    print("Direct C++ record identity and borrowed container overloads preserve values, copies and errors")
    guard CommandLine.arguments.contains("--benchmark") else { return }
    let swiftValues = (0..<1024).map { Smoke.Coordinate(x: Double($0), y: 2) }
    var nativeValues = djinni_generated.Engine_pointsArg0()
    nativeValues.reserve(swiftValues.count)
    for value in swiftValues { nativeValues.push_back(value) }
    func measure(_ call: () throws -> Int) throws -> Double {
        var count = 0
        let start = DispatchTime.now().uptimeNanoseconds
        for _ in 0..<1000 { count += try call() }
        let elapsed = DispatchTime.now().uptimeNanoseconds - start
        precondition(count == 1_024_000)
        return Double(elapsed) / 1000
    }
    var swiftTimes: [Double] = [], nativeTimes: [Double] = []
    for round in 0..<6 {
        if round % 2 == 0 {
            swiftTimes.append(try measure { try engine.points(swiftValues).count })
            nativeTimes.append(try measure { Int(try engine.points(nativeValues).size()) })
        } else {
            nativeTimes.append(try measure { Int(try engine.points(nativeValues).size()) })
            swiftTimes.append(try measure { try engine.points(swiftValues).count })
        }
    }
    let swift = swiftTimes.dropFirst().sorted()[2], native = nativeTimes.dropFirst().sorted()[2]
    print(String(format: "1024-coordinate overloads: Swift array %.1f ns, native vector %.1f ns, %.2fx", swift, native, swift / native))
}
