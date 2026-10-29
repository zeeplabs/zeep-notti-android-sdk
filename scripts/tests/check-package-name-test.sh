#!/usr/bin/env bash
# Self-test for scripts/check-package-name.sh (PKG-04).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-package-name.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'chmod -R u+rwx "$tmp" 2>/dev/null || true; rm -rf "$tmp"' EXIT

# fixture <name> <namespace> <file-relative-path> <file-content>
fixture() {
  local dir="$tmp/$1"
  mkdir -p "$dir/notti/src/main/java" "$dir/example/src/main/java" "$dir/example-manual-fcm/src/main/java"
  printf 'android {\n    namespace = "%s"\n}\n' "$2" >"$dir/notti/build.gradle.kts"
  if [[ -n "$3" ]]; then
    mkdir -p "$(dirname "$dir/$3")"
    printf '%s\n' "$4" >"$dir/$3"
  fi
  echo "$dir"
}

ok="$(fixture ok com.zeeplabs.notti notti/src/main/java/A.kt 'package com.zeeplabs.notti

import com.zeeplabs.notti.internal.X
// mentions com.notti in a comment only')"
expect 0 "com.zeeplabs.notti passes" "$script" "$ok"

pkg="$(fixture pkg com.zeeplabs.notti notti/src/main/java/B.kt 'package com.notti')"
expect 1 "package com.notti fails" "$script" "$pkg"

sub="$(fixture sub com.zeeplabs.notti example/src/main/java/C.kt 'package com.notti.inapp')"
expect 1 "package com.notti.<sub> in example fails" "$script" "$sub"

manual="$(fixture manual com.zeeplabs.notti example-manual-fcm/src/main/java/C.kt 'import com.notti.NottiBridge')"
expect 1 "import com.notti.* in example-manual-fcm fails" "$script" "$manual"

imp="$(fixture imp com.zeeplabs.notti notti/src/main/java/D.kt 'package com.zeeplabs.notti

import com.notti.NottiCore')"
expect 1 "import com.notti.* fails" "$script" "$imp"

java="$(fixture java com.zeeplabs.notti notti/src/main/java/E.java 'package com.notti;')"
expect 1 "Java package com.notti; fails" "$script" "$java"

prefix="$(fixture prefix com.zeeplabs.notti notti/src/main/java/F.kt 'package com.nottilabs')"
expect 0 "com.nottilabs is a different package" "$script" "$prefix"

ns="$(fixture ns com.notti "" "")"
expect 1 "wrong namespace fails" "$script" "$ns"

nobuild="$(fixture nobuild com.zeeplabs.notti "" "")"
rm "$nobuild/notti/build.gradle.kts"
expect 1 "missing build file fails" "$script" "$nobuild"

nosrc="$(fixture nosrc com.zeeplabs.notti "" "")"
rm -rf "$nosrc/example/src"
expect 1 "missing source dir fails" "$script" "$nosrc"

nomanual="$(fixture nomanual com.zeeplabs.notti "" "")"
rm -rf "$nomanual/example-manual-fcm/src"
expect 1 "missing example-manual-fcm source dir fails" "$script" "$nomanual"

if [[ "$(id -u)" != "0" ]]; then
  unreadable="$(fixture unreadable com.zeeplabs.notti notti/src/main/java/G.kt 'package com.zeeplabs.notti')"
  mkdir -p "$unreadable/notti/src/main/java/locked"
  chmod 000 "$unreadable/notti/src/main/java/locked"
  expect 1 "unreadable directory (grep status 2) fails" "$script" "$unreadable"
  chmod 755 "$unreadable/notti/src/main/java/locked"
fi

finish
