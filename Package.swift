// swift-tools-version:5.3
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "DjinniSupport",
    products: [
        .library(
            name: "DjinniSupport",
            targets: ["DjinniSupport"]
        ),
        .library(
            name: "DjinniSupportCpp",
            targets: ["DjinniSupportCpp"]
        ),
    ],
    targets: [
        .target(
            name: "DjinniSupport",
            path: "support-lib/objc"
        ),
        .target(
            name: "DjinniSupportCpp",
            path: "support-lib/cpp"
        ),
    ],
    cxxLanguageStandard: .cxx1z
)
