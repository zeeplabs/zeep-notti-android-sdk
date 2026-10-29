#!/usr/bin/env bash
# Fails when SDK or example app sources reference React Native (CORE-01): the SDK must build and run without it.
#
# Usage: scripts/check-no-react-native.sh [repo-root]
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
dirs=("$root/notti/src" "$root/example/src" "$root/example-manual-fcm/src")
for d in "${dirs[@]}"; do
  if [[ ! -d "$d" ]]; then
    echo "error: $d not found" >&2
    exit 1
  fi
done

# grep exits 0 on a match, 1 on none and 2 on an error; only 1 is a pass.
set +e
hits="$(grep -rnE --include='*.kt' --include='*.java' --include='*.kts' --include='*.xml' \
  'com\.facebook\.react|ReactApplicationContext|ReactContextBaseJavaModule|WritableMap|ReadableMap|NativeNottiSpec' \
  "${dirs[@]}")"
status=$?
set -e

case "$status" in
  1) echo "react native ok: no references" ;;
  0)
    echo "error: React Native reference in the SDK (CORE-01):" >&2
    echo "$hits" >&2
    exit 1
    ;;
  *)
    echo "error: grep failed with status $status" >&2
    exit 1
    ;;
esac
