#!/usr/bin/env bash
# Fails when any Kotlin/Java source declares or imports the `com.notti` package, or when the library namespace
# is not `com.zeeplabs.notti` (PKG-04, AD-003): react-native-notti 0.8.0 ships `com.notti.*`, and equal fully
# qualified names on one classpath fail the host build with duplicate classes.
#
# Usage: scripts/check-package-name.sh [repo-root]
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
build_file="$root/notti/build.gradle.kts"
dirs=("$root/notti/src" "$root/example/src")

if [[ ! -f "$build_file" ]]; then
  echo "error: $build_file not found" >&2
  exit 1
fi
for d in "${dirs[@]}"; do
  if [[ ! -d "$d" ]]; then
    echo "error: $d not found" >&2
    exit 1
  fi
done

if ! grep -qE '^[[:space:]]*namespace = "com\.zeeplabs\.notti"$' "$build_file"; then
  echo "error: $build_file must declare namespace = \"com.zeeplabs.notti\"" >&2
  exit 1
fi

# grep exits 0 on a match, 1 on none and 2 on an error; only 1 is a pass.
set +e
hits="$(grep -rnE --include='*.kt' --include='*.java' \
  '^[[:space:]]*(package|import)[[:space:]]+com\.notti([.;[:space:]]|$)' "${dirs[@]}")"
status=$?
set -e

case "$status" in
  1) echo "package ok: no com.notti sources" ;;
  0)
    echo "error: the com.notti package belongs to react-native-notti (AD-003); use com.zeeplabs.notti:" >&2
    echo "$hits" >&2
    exit 1
    ;;
  *)
    echo "error: grep failed with status $status" >&2
    exit 1
    ;;
esac
