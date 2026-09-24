# Kotlin/JS generator regression test

Run `bash generate.sh`, then `gradle jsNodeTest` (Gradle 9.4.1 / JDK 21+).
The OpenMobileMaps Gradle wrapper can also run this standalone project with `-p`.
Generated outputs stay untracked.

`--kotlin-kmp-js-out` generates actual bindings for the same common declarations
as Android/iOS. The generated `<prefix>DjinniJs.initialize(module)` registers
an initialized Emscripten module for static calls. Interfaces use explicit JS
property names and cached callback proxies; records and collections are
converted rather than exposing Kotlin implementation objects to JavaScript.

This fixture checks the WASM ABI for binary data, primitive typed arrays,
64-bit integers, sets, maps, and nested optional values. Application-level
map and callback tests live in OpenMobileMaps `maps-core/kmp/jsTest`.


The same fixture now has a Kotlin/Wasm target. `generate.sh` also uses
`--kotlin-kmp-wasm-out`; run `gradle wasmJsNodeTest jsNodeTest` to validate both
backends. The Wasm backend uses typed `JsAny` imports and
`<prefix>DjinniWasm.initialize(module)`. It emits independent actual declarations,
so it does not expose Kotlin/JS `dynamic` types to Wasm consumers.
