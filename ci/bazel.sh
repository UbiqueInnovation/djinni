#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
bazel_bin=${BAZEL_EXECUTABLE:-bazel}
output=$(mktemp -d)
trap 'rm -rf "$output"' EXIT
printf 'sample = record { value: string; }\n' > "$output/smoke.djinni"

"$bazel_bin" run //src:djinni -- --idl "$output/smoke.djinni" --cpp-out "$output/local" --ident-cpp-file FooBar
"$bazel_bin" run //generator:generator -- --idl "$output/smoke.djinni" --cpp-out "$output/packaged" --ident-cpp-file FooBar
(
    cd external-test
    "$bazel_bin" run @djinni//src:djinni -- --idl "$output/smoke.djinni" --cpp-out "$output/external" --ident-cpp-file FooBar
)
test -s "$output/local/Sample.hpp"
diff -r "$output/local" "$output/packaged"
diff -r "$output/local" "$output/external"
