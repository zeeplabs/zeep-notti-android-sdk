#!/usr/bin/env bash
# Self-test for scripts/check-no-url-open.sh (PUSH-12).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-no-url-open.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'chmod -R u+rwx "$tmp" 2>/dev/null || true; rm -rf "$tmp"' EXIT

# fixture <name> <relative-file> <content> [script-to-use]
fixture() {
  local dir="$tmp/$1"
  mkdir -p "$dir/notti/src/main/java"
  if [[ -n "$2" ]]; then
    mkdir -p "$(dirname "$dir/$2")"
    printf '%s\n' "$3" >"$dir/$2"
  fi
  echo "$dir"
}

expect 0 "clean tree passes" "$script" "$(fixture ok notti/src/main/java/A.kt 'val intent = Intent(context, NottiPermissionActivity::class.java)')"
expect 1 "ACTION_VIEW fails" "$script" "$(fixture view notti/src/main/java/B.kt 'Intent(Intent.ACTION_VIEW, uri)')"
expect 1 "CustomTabsIntent fails" "$script" "$(fixture tabs notti/src/main/java/C.kt 'CustomTabsIntent.Builder().build()')"
expect 1 "openUri fails" "$script" "$(fixture uri notti/src/main/java/D.kt 'handler.openUri(value)')"

# An allowlisted exact path passes; the same content elsewhere fails.
allowed="$(fixture allowed notti/src/main/java/Allowed.kt 'Intent(Intent.ACTION_VIEW, uri)')"
patched="$tmp/patched-check.sh"
sed 's|^ALLOWLIST=()$|ALLOWLIST=("notti/src/main/java/Allowed.kt")|' "$script" >"$patched"
chmod +x "$patched"
expect 0 "allowlisted exact path passes" "$patched" "$allowed"
other="$(fixture other notti/src/main/java/Other.kt 'Intent(Intent.ACTION_VIEW, uri)')"
expect 1 "non-allowlisted path fails with an allowlist set" "$patched" "$other"

nosrc="$tmp/nosrc"
mkdir -p "$nosrc"
expect 1 "missing source dir fails" "$script" "$nosrc"

if [[ "$(id -u)" != "0" ]]; then
  locked="$(fixture locked notti/src/main/java/E.kt 'val x = 1')"
  mkdir -p "$locked/notti/src/main/java/locked"
  chmod 000 "$locked/notti/src/main/java/locked"
  expect 1 "unreadable directory (grep status 2) fails" "$script" "$locked"
  chmod 755 "$locked/notti/src/main/java/locked"
fi

finish
