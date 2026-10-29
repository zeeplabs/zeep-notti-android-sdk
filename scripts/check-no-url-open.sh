#!/usr/bin/env bash
# Fails when SDK code opens a URL outside the allowlist (PUSH-12): a tapped push's data reaches the host
# untouched and the SDK never opens it. Only the files listed in ALLOWLIST (exact paths, relative to the repo
# root) may open a URL; in-app-messages adds the push-permission settings action and the AD-006 URL opener.
#
# Usage: scripts/check-no-url-open.sh [repo-root]
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
src="$root/notti/src/main"
ALLOWLIST=()

if [[ ! -d "$src" ]]; then
  echo "error: $src not found" >&2
  exit 1
fi

# grep exits 0 on a match, 1 on none and 2 on an error; only 1 is a pass.
set +e
hits="$(grep -rnE --include='*.kt' --include='*.java' \
  'ACTION_VIEW|CustomTabsIntent|\.openUri\(|launchUrl\(' "$src")"
status=$?
set -e

case "$status" in
  1)
    echo "url open ok: none outside the allowlist"
    exit 0
    ;;
  0) ;;
  *)
    echo "error: grep failed with status $status" >&2
    exit 1
    ;;
esac

violations=""
while IFS= read -r line; do
  file="${line%%:*}"
  rel="${file#"$root"/}"
  allowed=0
  for a in "${ALLOWLIST[@]+"${ALLOWLIST[@]}"}"; do
    if [[ "$rel" == "$a" ]]; then allowed=1; fi
  done
  if [[ "$allowed" == 0 ]]; then violations+="$line"$'\n'; fi
done <<<"$hits"

if [[ -n "$violations" ]]; then
  echo "error: the SDK opens a URL outside the allowlist (PUSH-12):" >&2
  printf '%s' "$violations" >&2
  exit 1
fi
echo "url open ok: only allowlisted files"
