#!/usr/bin/env python3
"""Run after `bazel build //src:djinni`; requires macOS and Swift 5.9+."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import sys
import os

non_throwing = "--non-throwing-properties" in sys.argv
fixture = Path(__file__).resolve().parent
root = fixture.parent.parent
with tempfile.TemporaryDirectory(prefix="djinni-swift-smoke-") as directory:
    tmp = Path(directory)
    shutil.copytree(root / "support-lib", tmp / "support-lib")
    subprocess.run([
        str(root / "src/run-assume-built"), "--idl", str(fixture / "smoke.djinni"),
        "--cpp-base-lib-include-prefix", "../support-lib/cpp/",
        "--yaml-out", str(tmp / "yaml"),
        "--cpp-out", str(tmp / "native"), "--swift-out", str(tmp / "swift"),
        "--ident-swiftxx-class", "SwiftFooBar", "--ident-swiftxx-file", "SwiftFooBar",
        "--swift-non-throwing", str(non_throwing).lower(),
        "--swift-module", "Smoke", "--swiftxx-out", str(tmp / "native"),
        "--kotlin-out", str(tmp / "kotlin"), "--java-package", "smoke",
        "--kotlin-kmp-common-out", str(tmp / "common"),
        "--kotlin-kmp-ios-out", str(tmp / "ios"),
        "--kotlin-kmp-package", "smoke", "--kotlin-kmp-ios-module", "SmokeObjc",
    ], check=True)
    assert "record deriving(sendable)" in (tmp / "yaml/sendable_value.yaml").read_text()
    (tmp / "consumer.djinni").write_text('@extern "yaml/sendable_value.yaml"\nconsumer = record { value: sendable_value; }\n')
    subprocess.run([str(root / "src/run-assume-built"), "--idl", str(tmp / "consumer.djinni"),
                    "--cpp-out", str(tmp / "consumer-native")], check=True)
    assert all("import Smoke" not in p.read_text().splitlines() for p in (tmp / "swift").glob("*.swift")), "Generated files must not import their own module"
    assert all("ParameterList params" not in p.read_text() for p in (tmp / "native").glob("Swift*.cpp")), "Callbacks must not allocate argument lists"
    assert all("ParameterList()" not in p.read_text() for p in (tmp / "swift").glob("*.swift")), "Swift calls must not allocate argument lists"
    for record in ["Payload", "Containers", "Associative", "InterfaceValues", "DataValues"]:
        native_code = (tmp / "swift" / (record + "+Private.swift")).read_text().split("public static func fromCpp", 1)[0]
        assert all(name not in native_code for name in ["ListMarshaller", "OptionalMarshaller", "MapMarshaller", "SetMarshaller"]), "Native record fields must bypass generic container packing"
    for name in ["Engine.swift", "Engine+Private.swift", "ContainerCallback+Private.swift", "MapCallback+Private.swift", "InterfaceCallback+Private.swift", "DataCallback+Private.swift"]:
        code = (tmp / "swift" / name).read_text()
        assert all(name not in code for name in ["ListMarshaller", "OptionalMarshaller", "MapMarshaller", "SetMarshaller"]), "Method containers must bypass generic packing"
    for name in ["Engine.swift", "Engine+Private.swift", "FutureCallback+Private.swift"]:
        assert "FutureMarshaller" not in (tmp / "swift" / name).read_text(), "Native async results must bypass generic future packing"
    for name in ["SwiftEngine.cpp", "SwiftFutureCallback.cpp"]:
        assert "FutureAdaptor" not in (tmp / "native" / name).read_text(), "Native futures must carry typed results"
    assert list((tmp / "common").rglob("*.kt"))
    assert list((tmp / "ios").rglob("*.kt"))
    for source in (tmp / "ios").rglob("*.kt"):
        assert all(token not in source.read_text() for token in ["swiftPMImport", "NSObject", "ObjCName"]), "KMP iOS contracts must not depend on the removed Objective-C bridge"
    shutil.copy(fixture / "engine.cpp", tmp / "native/engine_impl.cpp")
    shutil.copy(fixture / "legacy.hpp", tmp / "native")
    (tmp / "main").mkdir()
    for source in fixture.glob("*.swift"):
        shutil.copy(source, tmp / "main")
    if non_throwing:
        (tmp / "main/main.swift").write_text("""import Smoke
final class MutableState: State { var value: Int32 = 10 }
let counter = CounterInterface.create(initialValue: 37)
precondition(counter.value == 37)
let engine = Engine.create()
precondition(engine.default == 42)
engine.count = 9
precondition(engine.count == 9)
let state = MutableState()
precondition(engine.updateState(state) == 11 && state.value == 11)
let nativeState = Engine.state()
nativeState.value = 20
precondition(nativeState.value == 20)
print("Swift read/write properties and bidirectional protocol properties passed")
runFutureChecks()
""")
        (tmp / "main/benchmark.swift").unlink()
        (tmp / "main/cache_checks.swift").unlink()
    (tmp / "Package.swift").write_text('''// swift-tools-version:5.9
import PackageDescription
let package = Package(name: "SmokeTest", platforms: [.macOS(.v12)], targets: [
    .target(name: "DjinniSupportCxx", path: "support-lib", sources: ["swiftxx"], publicHeadersPath: "swiftxx", linkerSettings: [.linkedFramework("CoreFoundation")]),
    .target(name: "DjinniSupport", dependencies: ["DjinniSupportCxx"], path: "support-lib/swift", exclude: ["DJProtobuf.swift"], swiftSettings: [.interoperabilityMode(.Cxx)]),
    .target(name: "SmokeCxx", dependencies: ["DjinniSupportCxx"], path: "native", publicHeadersPath: ".", cxxSettings: [.headerSearchPath("../support-lib/cpp")]),
    .target(name: "Smoke", dependencies: ["SmokeCxx", "DjinniSupport"], path: "swift", swiftSettings: [.interoperabilityMode(.Cxx)]),
    .executableTarget(name: "SmokeTest", dependencies: ["Smoke"], path: "main", swiftSettings: [.interoperabilityMode(.Cxx)])
], cxxLanguageStandard: .cxx17)
''')
    configuration = "debug" if "--debug" in sys.argv else "release"
    sanitizer = ["--sanitize", "thread"] if "--thread-sanitizer" in sys.argv else []
    subprocess.run(["swift", "run", "--package-path", str(tmp), "-c", configuration] + sanitizer + ["SmokeTest"] + (["--benchmark"] if "--benchmark" in sys.argv else []), check=True, env={**os.environ, "TSAN_OPTIONS": "halt_on_error=1"})
