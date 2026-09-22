#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
../../src/build.sh
../../src/run-assume-built --idl interop.djinni \
  --kotlin-kmp-common-out generated/commonMain \
  --kotlin-kmp-js-out generated/jsMain \
  --kotlin-kmp-package test.interop --kotlin-kmp-bridge-prefix KM
