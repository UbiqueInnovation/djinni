#!/usr/bin/env bash
set -eu

generator="$1"
out="$TEST_TMPDIR/out"
idl="$TEST_TMPDIR/exposure.djinni"

cat > "$idl" <<'EOF'
default_api = interface {}
no_java = interface +c -j {}
no_objc = interface +c -o {}
no_web = interface +c -w {}
cpp_only = interface +c -j -o -w {}
EOF

"$generator" \
    --idl "$idl" \
    --cpp-out "$out/cpp" \
    --java-out "$out/java" \
    --java-package test \
    --kotlin-out "$out/kotlin" \
    --kotlin-kmp-common-out "$out/kmp/common" \
    --kotlin-kmp-android-out "$out/kmp/android" \
    --kotlin-kmp-ios-out "$out/kmp/ios" \
    --kotlin-kmp-package test \
    --kotlin-kmp-ios-module Test \
    --jni-out "$out/jni" \
    --objc-out "$out/objc" \
    --objcpp-out "$out/objc" \
    --wasm-out "$out/wasm" \
    --wasm-namespace test \
    --ts-out "$out/ts" \
    --ts-module test \
    --yaml-out "$out/yaml" \
    --yaml-out-file test.yaml

present() { test -f "$1" || { echo "missing: $1" >&2; exit 1; }; }
absent() { test ! -e "$1" || { echo "unexpected: $1" >&2; exit 1; }; }
contains() { grep -Fq "$2" "$1" || { echo "missing '$2' in $1" >&2; exit 1; }; }
omits() { ! grep -Fq "$2" "$1" || { echo "unexpected '$2' in $1" >&2; exit 1; }; }

# No annotation remains exposed to every generator.
present "$out/cpp/default_api.hpp"
present "$out/java/DefaultApi.java"
present "$out/kotlin/DefaultApi.kt"
present "$out/jni/default_api.hpp"
present "$out/objc/DefaultApi.h"
present "$out/objc/DefaultApi+Private.h"
present "$out/wasm/default_api.hpp"
present "$out/kmp/common/DefaultApi.kt"
present "$out/kmp/android/DefaultApi.kt"
present "$out/kmp/ios/DefaultApi.kt"
contains "$out/ts/test.ts" "export interface DefaultApi"

# Each exclusion only removes output for its platform family.
present "$out/cpp/no_java.hpp"
absent "$out/java/NoJava.java"
absent "$out/kotlin/NoJava.kt"
absent "$out/jni/no_java.hpp"
present "$out/objc/NoJava.h"
present "$out/wasm/no_java.hpp"
contains "$out/ts/test.ts" "export interface NoJava"
absent "$out/kmp/common/NoJava.kt"

present "$out/java/NoObjc.java"
present "$out/kotlin/NoObjc.kt"
present "$out/jni/no_objc.hpp"
absent "$out/objc/NoObjc.h"
present "$out/wasm/no_objc.hpp"
contains "$out/ts/test.ts" "export interface NoObjc"
absent "$out/kmp/common/NoObjc.kt"

present "$out/java/NoWeb.java"
present "$out/objc/NoWeb.h"
absent "$out/wasm/no_web.hpp"
omits "$out/ts/test.ts" "export interface NoWeb"
present "$out/kmp/common/NoWeb.kt"

present "$out/cpp/cpp_only.hpp"
absent "$out/java/CppOnly.java"
absent "$out/kotlin/CppOnly.kt"
absent "$out/jni/cpp_only.hpp"
absent "$out/objc/CppOnly.h"
absent "$out/wasm/cpp_only.hpp"
omits "$out/ts/test.ts" "export interface CppOnly"
absent "$out/kmp/common/CppOnly.kt"

contains "$out/yaml/test.yaml" "typedef: 'interface +c -j'"
contains "$out/yaml/test.yaml" "typedef: 'interface +c -o'"
contains "$out/yaml/test.yaml" "typedef: 'interface +c -w'"
contains "$out/yaml/test.yaml" "typedef: 'interface +c -o -j -w'"

cat > "$TEST_TMPDIR/invalid.djinni" <<'EOF'
hidden = interface +c -j {}
visible = interface +c {
    hidden(): hidden;
}
EOF

if "$generator" --idl "$TEST_TMPDIR/invalid.djinni" --java-out "$out/invalid" --java-package test; then
    echo "accepted an exposed interface referencing a hidden interface" >&2
    exit 1
fi
