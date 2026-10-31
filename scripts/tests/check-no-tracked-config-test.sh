#!/usr/bin/env bash
# Self-test for scripts/check-no-tracked-config.sh (EXR-06).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-no-tracked-config.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# repo <name> [relative-file...]: a git repo with the given files tracked.
repo() {
  local dir="$tmp/$1"
  shift
  mkdir -p "$dir"
  git -C "$dir" init -q
  local f
  for f in README.md "$@"; do
    mkdir -p "$(dirname "$dir/$f")"
    echo fixture >"$dir/$f"
    git -C "$dir" add -f "$f"
  done
  echo "$dir"
}

expect 0 "clean repo passes" "$script" "$(repo clean)"
expect 1 "tracked example/google-services.json fails" "$script" "$(repo gs example/google-services.json)"
expect 1 "tracked root google-services.json fails" "$script" "$(repo gsroot google-services.json)"
expect 1 "tracked example/notti.properties fails" "$script" "$(repo props example/notti.properties)"
expect 0 "tracked sample and fixture pass" "$script" \
  "$(repo samples example/notti.properties.sample scripts/tests/fixtures/google-services.fixture.json)"

untracked="$(repo untracked)"
echo fixture >"$untracked/google-services.json"
expect 0 "untracked google-services.json passes" "$script" "$untracked"

mkdir -p "$tmp/nogit"
expect 2 "not a git checkout fails with 2" "$script" "$tmp/nogit"
expect 0 "the repository passes" "$script"

finish
