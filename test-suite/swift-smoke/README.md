# Swift bridge integration check

This fork backports Snapchat/djinni PR #178 (merge `32b69852625e990711b0e960e84ffa6bfd94b7b2`). It preserves the Kotlin/KMP generators and adapts the Swift data bridge to this fork's deferred `DataRef` implementation.

From the repository root:

```sh
bazel build //src:djinni --repo_env=BAZEL_USE_CPP_ONLY_TOOLCHAIN=1
python3 test-suite/swift-smoke/run.py
python3 test-suite/swift-smoke/run.py --debug
python3 test-suite/swift-smoke/run.py --non-throwing-properties
python3 test-suite/swift-smoke/run.py --thread-sanitizer
python3 test-suite/swift-smoke/run.py --benchmark
```

The check generates fresh bindings, builds the Swift and C++ runtimes with SwiftPM, and runs record conversion, virtual calls, Swift callbacks, C++ exception translation, and DataRef ownership checks. It also verifies Kotlin/KMP outputs are generated. SwiftProtobuf is excluded from this small check. The upstream broader test target is `//test-suite:djinni-swift-tests`; its legacy Bazel Apple rules need updating for Bazel 7.

Generate Swift bindings with `--swift-out`, `--swift-module`, and `--swiftxx-out`. Use `--ident-swiftxx-class SwiftFooBar --ident-swiftxx-file SwiftFooBar` to distinguish bridge headers from the engine's headers. Interfaces implemented by Swift need `+sw` (or `+nc` for all non-C++ platforms); an existing `+o` modifier alone does not enable Swift implementations.

The upstream bridge uses Swift types and generated C++ adapters, not direct exposure of every engine type. Its runtime currently uses Swift 5 language mode in this check. The root `Package.swift` now exports `DjinniSupport` (Swift), `DjinniSupportCxx` (Swift/C++ adapters), and `DjinniSupportCpp` (native headers). Protobuf support is excluded from the SwiftPM product.

Migration options added by this fork: `--ident-swift-type MCFooBar` preserves a platform type prefix, `--ident-swift-enum FOO_BAR` preserves enum spelling, and `--swift-non-throwing true` emits non-throwing methods that trap translated native exceptions. Without the latter, upstream throwing behavior is retained. `DataRef` uses Swift `Data`; the C++ adapter consumes the retained Foundation object used during conversion. `DJPromise` provides the producer side of `DJFuture` for existing asynchronous loaders.

## Native Swift API and typed transport

Djinni type names remain authoritative, including suffixes such as `Interface`. C++-only interfaces become final Swift classes; Swift-implementable interfaces remain protocols. Factories use named arguments. Unambiguous getters become properties, with matching setters in non-throwing mode. Future-returning methods use `async throws` in both modes. These are breaking changes to the initial upstream Swift API.

Generated calls use typed C++ entry points and stack-owned callback payloads instead of heap-backed argument lists. Supported records and containers convert directly to native values. Successful results use inline storage; futures carry typed results. Interface conversion still uses an ownership/identity cache, and unsupported external types retain generic conversion. YAML's `swift.native` metadata enables cross-package conversion; regenerate dependencies first.

Nongeneric, non-extended records are aliases of imported C++ records. Primitive fields are directly imported; Swift field conveniences for other representations convert on access and retain the original names, with native storage available as `__djinni_<cppFieldName>`. Labeled initializers are extensions. Typed calls bypass record converters, and synchronous concrete record parameters are borrowed. Generic and C++-extended records retain the existing fallback.

Regenerate C++ headers and Swift together, using matching naming options. Clang field annotations change only Swift import names, not C++ names or record layout. Consumer Swift targets must enable C++ interoperability because record aliases are now part of the public API.

Synchronous concrete methods/factories also accept borrowed native collections through overloads of the same name. The Swift collection overload remains available. Optional-only arguments do not create an overload, preserving unambiguous `nil` calls. Return-only collection properties and async collection APIs still expose Swift collections.

Enums and flags conform to checked `Sendable`. Imported C++ records require an unchecked conformance; explicit `deriving(sendable)` is accepted only for value-only Sendable fields validated by the generator. Interfaces do not gain Sendable. Both `deriving(parcelable)` and `deriving(codable)` generate explicit Swift Codable implementations with the previous field keys and optional omission behavior, emitted once if both are specified. Android parcelable behavior is unchanged. The smoke check round-trips nested records and optional values through JSON, proves C++/Swift record identity, and verifies native collection overloads, independent copies, errors and `nil` overload resolution.

Runtime checks cover container conversion, identity, concurrent cache access, binary/DataRef ownership, copy-on-write, exceptions, broken promises, reentrancy and completion/cancellation races. Cancelling an await ends that waiter; it cannot cancel the underlying C++ producer, whose future API has no cancellation hook. Interface wrappers do not gain a blanket Sendable conformance.

The release benchmark compares the old argument-packing sequence with the new bridge for identical operations, alternating order and reporting warmed-up medians. On the validation Mac, coordinate calls measured 221.9 → 5.6 ns, 1,024-coordinate arrays 200,643.5 → 1,940.8 ns, and 64 KiB binary round trips 13,565.9 → 7,918.7 ns. DataRef was effectively unchanged. These are host bridge microbenchmarks, not application frame-rate or KMP performance measurements. The callback comparison uses the current typed reverse bridge on both sides.

## KMP Swift-export adapters

KMP iOS generation now emits Kotlin values and delegate/factory contracts instead of imports of the Objective-C API. `--kotlin-kmp-swift-out <directory>` generates Swift adapters; `--kotlin-kmp-swift-module <module>` selects their Kotlin Swift-export module. Put adapters in a separate target depending on the native Swift module and the exported Kotlin module, and install their generated factories before use. Common and Android generation remain available. This replaces the previous KMP iOS integration and requires consumer migration.

The consuming OpenMobileMaps workspace validates seven separately exported modules, callbacks, collections, dates, binary values, services and async lifetimes with Kotlin 2.4.20, plus a production UIKit rendering sample. Those integration checks live in the consumer repository; this repository's smoke check verifies Kotlin generation but does not compile Kotlin exports. Generic record/interface adapters are unsupported. Kotlin enum collection compatibility uses `_ObjectiveCBridgeable`, so the KMP path does not eliminate the Objective-C runtime. The Swift/C++ runtime itself builds in Swift 5 language mode, and SwiftProtobuf remains outside the SwiftPM product and this smoke check.

The native-container overload benchmark now compares the current Swift-array convenience against the borrowed native-vector overload for the same 1,024-coordinate C++ round trip: one release run measured 2,599.8 ns versus 211.7 ns (12.28×). Input construction and result checks are outside the timed comparison. Both include the C++ implementation's return copy. The consumer validates all 144 active records as direct aliases while preserving all 402 type names. KMP record conversions borrow their C++ input to avoid a redundant record copy before field conversion.
