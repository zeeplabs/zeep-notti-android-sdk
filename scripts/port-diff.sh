#!/usr/bin/env bash
# Dev tool (not CI): shows what a ported file changed against its react-native-notti source, ignoring the
# mechanical port changes: the provenance header, the `@file:Suppress` block (one or many lines), the `package` and `import` lines,
# blank lines and all whitespace (AD-009). Every remaining hunk must be an allowed change of the feature's
# design or a listed SPEC_DEVIATION.
#
# Usage: scripts/port-diff.sh <rn-path-under-android/src> <ported-file> [rn-repo] [tag]
# Exit: 0 identical after normalization, 1 differences shown, 2 usage or input error.
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <rn-path-under-android/src> <ported-file> [rn-repo] [tag]" >&2
  exit 2
fi
src="$1"
ported="$2"
root="$(cd "$(dirname "$0")/.." && pwd)"
rn="${3:-$root/../zeep-notti-react-native}"
tag="${4:-v0.8.0}"

if [[ ! -f "$ported" ]]; then
  echo "error: $ported not found" >&2
  exit 2
fi

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Drops the header, a one-line or multi-line `@file:Suppress(...)` block, package/import lines and blank lines.
normalize() {
  awk '
    in_suppress { if ($0 ~ /^[[:space:]]*\)[[:space:]]*$/) in_suppress = 0; next }
    /^[[:space:]]*@file:Suppress\(/ { if ($0 !~ /\)[[:space:]]*$/) in_suppress = 1; next }
    /^[[:space:]]*(\/\/ Ported from react-native-notti|package |import )/ { next }
    /^[[:space:]]*$/ { next }
    { print }
  '
}

git -C "$rn" show "$tag:android/src/$src" | normalize >"$tmp/rn.kt"
normalize <"$ported" >"$tmp/ported.kt"

set +e
diff -w -U2 --label "rn:$src" --label "ported:$ported" "$tmp/rn.kt" "$tmp/ported.kt"
status=$?
set -e
case "$status" in
  0) echo "port-diff: identical after normalization" ;;
  1) exit 1 ;;
  *) exit 2 ;;
esac
