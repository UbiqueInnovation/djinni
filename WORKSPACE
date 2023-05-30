workspace(name="snap_djinni")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "remote_java_tools",
    sha256 = "cbb62ecfef61568ded46260a8e8e8430755db7ec9638c0c7ff668a656f6c042f",
    urls = [
            "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.3/java_tools-v12.3.zip",
            "https://github.com/bazelbuild/java_tools/releases/download/java_v12.3/java_tools-v12.3.zip",
    ],
)
http_archive(
    name = "remote_java_tools_linux",
    sha256 = "32157b5218b151009f5b99bf5e2f65e28823d269dfbba8cd57e7da5e7cdd291d",
    urls = [
            "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.3/java_tools_linux-v12.3.zip",
            "https://github.com/bazelbuild/java_tools/releases/download/java_v12.3/java_tools_linux-v12.3.zip",
    ],
)
http_archive(
    name = "remote_java_tools_windows",
    sha256 = "ec6f91387d2353eacb0ca0492f35f68c5c7b0e7a80acd1fb825088b4b069fab1",
    urls = [
            "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.3/java_tools_windows-v12.3.zip",
            "https://github.com/bazelbuild/java_tools/releases/download/java_v12.3/java_tools_windows-v12.3.zip",
    ],
)
http_archive(
    name = "remote_java_tools_darwin_x86_64",
    sha256 = "3c3fb1967a0f35c73ff509505de53ca4611518922a6b7c8c22a468aa7503132c",
    urls = [
            "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.3/java_tools_darwin_x86_64-v12.3.zip",
            "https://github.com/bazelbuild/java_tools/releases/download/java_v12.3/java_tools_darwin_x86_64-v12.3.zip",
    ],
)
http_archive(
    name = "remote_java_tools_darwin_arm64",
    sha256 = "29aa0c2de4e3cf45bc55d2995ba803ecbd1173a8d363860abbc309551db7931b",
    urls = [
            "https://mirror.bazel.build/bazel_java_tools/releases/java/v12.3/java_tools_darwin_arm64-v12.3.zip",
            "https://github.com/bazelbuild/java_tools/releases/download/java_v12.3/java_tools_darwin_arm64-v12.3.zip",
    ],
)

http_archive(
    name = "rules_python",
    url = "https://github.com/bazelbuild/rules_python/releases/download/0.0.2/rules_python-0.0.2.tar.gz",
    strip_prefix = "rules_python-0.0.2",
    sha256 = "b5668cde8bb6e3515057ef465a35ad712214962f0b3a314e551204266c7be90c",
)

load("@rules_python//python:repositories.bzl", "py_repositories")
py_repositories()

# Only needed if using the packaging rules.
load("@rules_python//python:pip.bzl", "pip_repositories")
pip_repositories()

# zlib
http_archive(
    name = "zlib",
    build_file = "@com_google_protobuf//:third_party/zlib.BUILD",
    sha256 = "c3e5e9fdd5004dcb542feda5ee4f0ff0744628baf8ed2dd5d66f8ca1197cb1a1",
    strip_prefix = "zlib-1.2.11",
    urls = [
        "https://mirror.bazel.build/zlib.net/zlib-1.2.11.tar.gz",
        "https://zlib.net/zlib-1.2.11.tar.gz",
    ],
)

load("//bzl:deps.bzl", "djinni_deps")
djinni_deps()
load("//bzl:scala_config.bzl", "djinni_scala_config")
djinni_scala_config()
load("//bzl:setup_deps.bzl", "djinni_setup_deps")
djinni_setup_deps()

# --- Everything below is only used for examples and tests

# android_sdk_repository fails to find build_tools if we don't explicitly set a version.
#android_sdk_repository(name = "androidsdk", build_tools_version = "32.0.0")
#android_ndk_repository(name = "androidndk", api_level = 21)

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

build_bazel_rules_apple_version = "1.0.1"
http_archive(
    name = "build_bazel_rules_apple",
    sha256 = "36072d4f3614d309d6a703da0dfe48684ec4c65a89611aeb9590b45af7a3e592",
    url = "https://github.com/bazelbuild/rules_apple/releases/download/{0}/rules_apple.{0}.tar.gz"
        .format(build_bazel_rules_apple_version),
)

build_bazel_rules_swift_version = "1.0.0"
http_archive(
    name = "build_bazel_rules_swift",
    sha256 = "12057b7aa904467284eee640de5e33853e51d8e31aae50b3fb25d2823d51c6b8",
    url = "https://github.com/bazelbuild/rules_swift/releases/download/{0}/rules_swift.{0}.tar.gz"
        .format(build_bazel_rules_swift_version),
)

build_bazel_apple_support_version = "1.0.0"
http_archive(
    name = "build_bazel_apple_support",
    sha256 = "df317473b5894dd8eb432240d209271ebc83c76bb30c55481374b36ddf1e4fd1",
    url = "https://github.com/bazelbuild/apple_support/releases/download/{0}/apple_support.{0}.tar.gz"
        .format(build_bazel_apple_support_version),
)

rules_kotlin_version = "legacy-1.3.0"
http_archive(
    name = "io_bazel_rules_kotlin",
    url = "https://github.com/bazelbuild/rules_kotlin/archive/{}.zip".format(rules_kotlin_version),
    type = "zip",
    strip_prefix = "rules_kotlin-{}".format(rules_kotlin_version),
    sha256 = "4fd769fb0db5d3c6240df8a9500515775101964eebdf85a3f9f0511130885fde",
)

load("@build_bazel_rules_apple//apple:repositories.bzl", "apple_rules_dependencies")
load("@build_bazel_rules_swift//swift:repositories.bzl", "swift_rules_dependencies")
load("@build_bazel_apple_support//lib:repositories.bzl", "apple_support_dependencies")
load("@io_bazel_rules_kotlin//kotlin:kotlin.bzl", "kotlin_repositories", "kt_register_toolchains")

apple_rules_dependencies()
swift_rules_dependencies()
apple_support_dependencies()

kotlin_repositories()
kt_register_toolchains()

emsdk_version = "3.1.8"

http_archive(
    name = "emsdk",
    strip_prefix = "emsdk-%s/bazel" % emsdk_version,
    type = "zip",
    url = "https://github.com/emscripten-core/emsdk/archive/%s.zip" % emsdk_version,
    sha256 = "7795202a50ab09958d8943f79110de4386ff0f38bf4c97ec1a896885f28fe1cf",
)

load("@emsdk//:deps.bzl", emsdk_deps = "deps")
emsdk_deps()

load("@emsdk//:emscripten_deps.bzl", emsdk_emscripten_deps = "emscripten_deps")
emsdk_emscripten_deps()
