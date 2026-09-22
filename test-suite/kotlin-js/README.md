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
