#!/usr/bin/env bash

set -euo pipefail

echo "--- Check Generated files"
./examples/run_djinni.sh
./perftest/run_djinni.sh
./test-suite/run_djinni.sh

if test -z "$(git status --porcelain -- examples/generated-src perftest/generated-src test-suite/generated-src)"; then
  echo "--- Success! Generated files are clean."
else
  echo "--- Failed! Generated files are dirty after running generator."
  git status --short -- examples/generated-src perftest/generated-src test-suite/generated-src
  git --no-pager diff -- examples/generated-src perftest/generated-src test-suite/generated-src
  exit 1
fi
