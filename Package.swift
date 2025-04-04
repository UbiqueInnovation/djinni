// swift-tools-version:5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "DjinniSupport",
    products: [
        .library(
            name: "DjinniSupport",
            targets: ["DjinniSupport"]
        )
    ],
    targets: [
        .target(
            name: "DjinniSupport",
            path: "support-lib/objc",
            publicHeadersPath: "",
            cxxSettings: [.define("DATAREF_OBJC")]
        )
    ],
    cxxLanguageStandard: .cxx17
)
