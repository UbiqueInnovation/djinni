// swift-tools-version:5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "DjinniSupport",
    products: [
        .library(
            name: "DjinniSupport",
            targets: ["DjinniSupport"]
        ),
        .plugin(name: "DjinniBuildPlugin", targets: ["DjinniBuildPlugin"])
    ],
    targets: [
        .target(
            name: "DjinniSupport",
            path: "support-lib/objc",
            publicHeadersPath: ""
        ),
        .plugin(
            name: "DjinniBuildPlugin",
            capability: .buildTool(),
            dependencies: [/*.target(name: "DjinniBinary")*/]
            ),
//        .executableTarget(name: "DjinniBinary", resources: [.copy("djinni-binary/djinni")])
    ],
    cxxLanguageStandard: .cxx17
)
