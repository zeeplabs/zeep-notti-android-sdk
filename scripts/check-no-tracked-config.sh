#!/usr/bin/env bash
# Fails when app config is tracked by git (EXR-06): an integrator's google-services.json or the example's
# notti.properties hold real ids and keys and stay local (AD-016). The tracked notti.properties.sample and
# scripts/tests/fixtures/google-services.fixture.json do not match.
#
# Usage: scripts/check-no-tracked-config.sh [repo-root]
# Exit: 0 clean, 1 tracked config found, 2 not a git checkout.
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"

if ! files="$(git -C "$root" ls-files 2>/dev/null)"; then
  echo "error: $root is not a git checkout" >&2
  exit 2
fi

# grep exits 0 on a match, 1 on none; anything else is an error.
set +e
hits="$(grep -E '(^|/)(google-services\.json|notti\.properties)$' <<<"$files")"
status=$?
set -e

case "$status" in
  1)
    echo "tracked config ok: none"
    exit 0
    ;;
  0)
    echo "error: app config is tracked by git; remove it with git rm --cached and keep it local (EXR-06):" >&2
    printf '%s\n' "$hits" >&2
    exit 1
    ;;
  *)
    echo "error: grep failed with status $status" >&2
    exit 2
    ;;
esac
