#!/usr/bin/env python3
"""Run after `bazel build //src:djinni`; requires macOS and Swift 5.9+."""
from pathlib import Path
import shutil
import subprocess
import tempfile

fixture = Path(__file__).resolve().parent
root = fixture.parent.parent
with tempfile.TemporaryDirectory(prefix="djinni-swift-smoke-") as directory:
    tmp = Path(directory)
    shutil.copytree(root / "support-lib", tmp / "support-lib")
    subprocess.run([
        str(root / "src/run-assume-built"), "--idl", str(fixture / "smoke.djinni"),
        "--cpp-base-lib-include-prefix", "../support-lib/cpp/",
        "--cpp-out", str(tmp / "native"), "--swift-out", str(tmp / "swift"),
        "--ident-swiftxx-class", "SwiftFooBar", "--ident-swiftxx-file", "SwiftFooBar",
        "--swift-module", "Smoke", "--swiftxx-out", str(tmp / "native"),
        "--kotlin-out", str(tmp / "kotlin"), "--java-package", "smoke",
        "--kotlin-kmp-common-out", str(tmp / "common"),
        "--kotlin-kmp-ios-out", str(tmp / "ios"),
        "--kotlin-kmp-package", "smoke", "--kotlin-kmp-ios-module", "SmokeObjc",
    ], check=True)
    assert list((tmp / "common").rglob("*.kt"))
    assert list((tmp / "ios").rglob("*.kt"))
    shutil.copy(fixture / "engine.cpp", tmp / "native/engine_impl.cpp")
    (tmp / "main").mkdir()
    shutil.copy(fixture / "main.swift", tmp / "main")
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
    subprocess.run(["swift", "run", "--package-path", str(tmp), "SmokeTest"], check=True)
