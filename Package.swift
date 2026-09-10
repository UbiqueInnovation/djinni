// swift-tools-version:6.1
import PackageDescription
let package = Package(
    name: "DjinniSupport",
    platforms: [.iOS(.v14), .macOS(.v12)],
    products: [
        .library(name: "DjinniSupport", targets: ["DjinniSupport"]),
        .library(name: "DjinniSupportCpp", targets: ["DjinniSupportCpp"]),
        .library(name: "DjinniSupportCxx", targets: ["DjinniSupportCxx"]),
    ],
    targets: [
        .target(name: "DjinniSupportCpp", path: "support-lib/cpp", publicHeadersPath: "."),
        .target(name: "DjinniSupportCxx", dependencies: ["DjinniSupportCpp"], path: "support-lib/swiftxx", publicHeadersPath: ".", linkerSettings: [.linkedFramework("CoreFoundation")]),
        .target(name: "DjinniSupport", dependencies: ["DjinniSupportCxx"], path: "support-lib/swift", exclude: ["DJProtobuf.swift"], swiftSettings: [.interoperabilityMode(.Cxx), .swiftLanguageMode(.v5)]),
    ],
    cxxLanguageStandard: .cxx17
)
