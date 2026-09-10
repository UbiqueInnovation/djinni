# Swift bridge integration check

This fork backports Snapchat/djinni PR #178 (merge `32b69852625e990711b0e960e84ffa6bfd94b7b2`). It preserves the Kotlin/KMP generators and adapts the Swift data bridge to this fork's deferred `DataRef` implementation.

From the repository root:

```sh
bazel build //src:djinni --repo_env=BAZEL_USE_CPP_ONLY_TOOLCHAIN=1
python3 test-suite/swift-smoke/run.py
```

The check generates fresh bindings, builds the Swift and C++ runtimes with SwiftPM, and runs record conversion, virtual calls, Swift callbacks, C++ exception translation, and DataRef ownership checks. It also verifies Kotlin/KMP outputs are generated. SwiftProtobuf is excluded from this small check. The upstream broader test target is `//test-suite:djinni-swift-tests`; its legacy Bazel Apple rules need updating for Bazel 7.

Generate Swift bindings with `--swift-out`, `--swift-module`, and `--swiftxx-out`. Use `--ident-swiftxx-class SwiftFooBar --ident-swiftxx-file SwiftFooBar` to distinguish bridge headers from the engine's headers. Interfaces implemented by Swift need `+sw` (or `+nc` for all non-C++ platforms); an existing `+o` modifier alone does not enable Swift implementations.

The upstream bridge uses Swift types and generated C++ adapters, not direct exposure of every engine type. Its runtime currently uses Swift 5 language mode in this check. The root `Package.swift` now exports `DjinniSupport` (Swift), `DjinniSupportCxx` (Swift/C++ adapters), and `DjinniSupportCpp` (native headers). Protobuf support is excluded from the SwiftPM product.

Migration options added by this fork: `--ident-swift-type MCFooBar` preserves a platform type prefix, `--ident-swift-enum FOO_BAR` preserves enum spelling, and `--swift-non-throwing true` emits non-throwing methods that trap translated native exceptions. Without the latter, upstream throwing behavior is retained. `DataRef` uses Swift `Data`; the C++ adapter consumes the retained Foundation object used during conversion. `DJPromise` provides the producer side of `DJFuture` for existing asynchronous loaders.
