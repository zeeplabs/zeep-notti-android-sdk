#!/usr/bin/env bash
# Self-test for scripts/port-file.sh and scripts/port-diff.sh against a throwaway git repo shaped like RN.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
port_file="$here/../port-file.sh"
port_diff="$here/../port-diff.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

rn="$tmp/rn"
mkdir -p "$rn/android/src/main/java/com/notti"
cat >"$rn/android/src/main/java/com/notti/Sample.kt" <<'KT'
package com.notti

import com.facebook.react.bridge.Promise

/**
 * Doc line.
 */
class Sample {
  fun f(): Int {
    return 1
  }
}
KT
git -C "$rn" init -q
git -C "$rn" -c user.name=t -c user.email=t@example.invalid add -A
git -C "$rn" -c user.name=t -c user.email=t@example.invalid commit -qm init
git -C "$rn" tag v0.0.1

ported="$tmp/out/Sample.kt"
expect 0 "port-file copies" "$port_file" main/java/com/notti/Sample.kt "$ported" "$rn" v0.0.1

check_line() {
  grep -qxF "$1" "$ported"
}
expect 0 "provenance header" check_line "// Ported from react-native-notti v0.0.1 ($(git -C "$rn" rev-parse --short HEAD)): android/src/main/java/com/notti/Sample.kt"
expect 0 "package renamed" check_line "package com.zeeplabs.notti"
expect 0 "2-space level becomes 4" check_line "    fun f(): Int {"
expect 0 "4-space level becomes 8" check_line "        return 1"
expect 0 "KDoc continuation keeps its odd space" check_line " * Doc line."

expect 0 "port-diff: mechanical changes only" "$port_diff" main/java/com/notti/Sample.kt "$ported" "$rn" v0.0.1
sed -i.bak 's/return 1/return 2/' "$ported"
expect 1 "port-diff: a code change shows" "$port_diff" main/java/com/notti/Sample.kt "$ported" "$rn" v0.0.1
expect 2 "port-diff: missing ported file" "$port_diff" main/java/com/notti/Sample.kt "$tmp/none.kt" "$rn" v0.0.1
expect 2 "port-file: no arguments" "$port_file"

finish
