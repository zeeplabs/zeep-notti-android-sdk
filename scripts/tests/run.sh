#!/usr/bin/env bash
# Runs every script self-test (scripts/tests/*-test.sh); fails if any fails.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
status=0
for t in "$here"/*-test.sh; do
  echo "# $(basename "$t")"
  "$t" || status=1
done
exit "$status"
