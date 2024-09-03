import PackagePlugin
import Foundation


@main
struct DjinniBuildPlugin: BuildToolPlugin {
    func createBuildCommands(context: PluginContext, target: Target) throws -> [Command] {
//        let tool = try context.tool(named: "sh")

        let scriptPath = "/private/var/tmp/_bazel_maerki/adf6de882066ddedc3d50169a3e9a92d/execroot/_main/bazel-out/darwin_arm64-fastbuild/bin/src/djinni" // context.package.directory.appending(subpath: "Plugins/DjinniBuildPlugin/djinni")

//        guard let scriptPath = Bundle.main.path(forResource: "run", ofType: "sh", inDirectory: "src") else {
//            throw CustomError.scriptNotFound
//        }

        guard let target = target as? SourceModuleTarget else {
            return []
        }


        return target.sourceFiles(withSuffix: ".djinni").map { inputFile in

//            let stem = inputFile.path.stem

            let djinniOutDir = context.pluginWorkDirectory

//            let djinniOutDir = "\(context.pluginWorkDirectory.string)/djinni"
            let objcOut = "\(djinniOutDir)/objc"
            let objcPrefix = "MC"
            let objcppOut = "\(djinniOutDir)/objcpp"
            let cppOut = "\(djinniOutDir)/headers"
            let yamlOut = "\(djinniOutDir)/yaml"
            let hppExt = "h"

            let identCpp = "FooBar"
            let identCppField = "fooBar"
            let identCppEnum = "FOO_BAR"
            let identCppMethod = "fooBar"

            let arguments = [
                "--cpp-out", cppOut,
                "--hpp-ext", hppExt,
                "--ident-cpp-type", identCpp,
                "--ident-cpp-file", identCpp,
                "--ident-cpp-enum", identCppEnum,
                "--ident-cpp-type-param", identCppMethod,
                "--ident-cpp-method", identCppMethod,
                "--ident-cpp-local", identCppMethod,
                "--ident-cpp-field", identCppField,
                "--objc-out", objcOut,
                "--objcpp-out", objcppOut,
                "--objc-type-prefix", objcPrefix,
                "--ident-objc-enum", identCppEnum,
                "--objc-strict-protocols", "false",
                "--yaml-out", yamlOut,
                "--idl", inputFile.path.string
            ]

            let outputFiles = [
                "\(objcOut)/*.m",
                "\(objcOut)/*.h",
                "\(objcppOut)/*.h",
                "\(objcppOut)/*.mm",
                "\(cppOut)/*.cpp",
                "\(cppOut)/*.h"
            ]

            return .buildCommand(
                displayName: "Processing \(inputFile.path.lastComponent)",
                executable: Path(scriptPath),
                arguments: arguments,
                inputFiles: [inputFile.path],
                outputFiles: outputFiles.map { Path($0) }
            )
        }
    }
}

enum CustomError: Error {
    case scriptNotFound
    case msg(String)
}
