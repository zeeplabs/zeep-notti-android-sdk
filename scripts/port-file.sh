#!/usr/bin/env bash
# Dev tool (not CI): copies one Kotlin file from react-native-notti at the port baseline into this repo with the
# mechanical port changes only (AD-002, AD-009): provenance header, `package com.notti` -> `com.zeeplabs.notti`,
# and 2 -> 4 space indentation. Odd leading spaces (KDoc ` * ` continuation lines) keep their extra space.
# Everything else (RN imports, visibility, logger, statics) is edited by hand afterwards, per the feature's
# design "allowed changes", and checked with scripts/port-diff.sh.
#
# Usage: scripts/port-file.sh <rn-path-under-android/src> <destination> [rn-repo] [tag]
#   e.g. scripts/port-file.sh main/java/com/notti/NottiEventStore.kt notti/src/main/java/com/zeeplabs/notti/NottiEventStore.kt
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "usage: $0 <rn-path-under-android/src> <destination> [rn-repo] [tag]" >&2
  exit 2
fi
src="$1"
dest="$2"
root="$(cd "$(dirname "$0")/.." && pwd)"
rn="${3:-$root/../zeep-notti-react-native}"
tag="${4:-v0.8.0}"

commit="$(git -C "$rn" rev-parse --short "$tag^{commit}")"
content="$(git -C "$rn" show "$tag:android/src/$src")"

mkdir -p "$(dirname "$dest")"
{
  echo "// Ported from react-native-notti $tag ($commit): android/src/$src"
  printf '%s\n' "$content" |
    sed -E 's/^package com\.notti$/package com.zeeplabs.notti/' |
    perl -pe 's/^( +)/" " x (2 * (length($1) - length($1) % 2) + length($1) % 2)/e'
} >"$dest"
echo "ported $src -> $dest"
