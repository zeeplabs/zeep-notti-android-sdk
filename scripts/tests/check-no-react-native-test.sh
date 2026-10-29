#!/usr/bin/env bash
# Self-test for scripts/check-no-react-native.sh (CORE-01).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-no-react-native.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'chmod -R u+rwx "$tmp" 2>/dev/null || true; rm -rf "$tmp"' EXIT

# fixture <name> <file-content or empty>
fixture() {
  local dir="$tmp/$1"
  mkdir -p "$dir/notti/src/main/java" "$dir/example/src/main/java" "$dir/example-manual-fcm/src/main/java"
  if [[ -n "$2" ]]; then printf '%s\n' "$2" >"$dir/notti/src/main/java/A.kt"; fi
  echo "$dir"
}

ok="$(fixture ok 'package com.zeeplabs.notti

internal class Core')"
expect 0 "clean tree passes" "$script" "$ok"
expect 1 "import com.facebook.react fails" "$script" "$(fixture imp 'import com.facebook.react.bridge.Promise')"
expect 1 "ReactApplicationContext fails" "$script" "$(fixture ctx 'class M(c: ReactApplicationContext)')"
expect 1 "WritableMap fails" "$script" "$(fixture wm 'val m: WritableMap? = null')"
expect 1 "NativeNottiSpec fails" "$script" "$(fixture spec 'class M : NativeNottiSpec()')"

manual="$(fixture manual '')"
printf '%s\n' 'import com.facebook.react.bridge.Promise' >"$manual/example-manual-fcm/src/main/java/B.kt"
expect 1 "React Native in example-manual-fcm fails" "$script" "$manual"

nomanual="$(fixture nomanual '')"
rm -rf "$nomanual/example-manual-fcm/src"
expect 1 "missing example-manual-fcm source dir fails" "$script" "$nomanual"

nosrc="$(fixture nosrc '')"
rm -rf "$nosrc/example/src"
expect 1 "missing source dir fails" "$script" "$nosrc"

if [[ "$(id -u)" != "0" ]]; then
  locked="$(fixture locked 'package com.zeeplabs.notti')"
  mkdir -p "$locked/notti/src/main/java/locked"
  chmod 000 "$locked/notti/src/main/java/locked"
  expect 1 "unreadable directory (grep status 2) fails" "$script" "$locked"
  chmod 755 "$locked/notti/src/main/java/locked"
fi

finish
