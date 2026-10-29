#!/usr/bin/env bash
# Self-test for scripts/check-version.sh (PKG-07): one passing fixture and one per failure mode.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-version.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# fixture <name> <version-line> <changelog-heading or empty> <readme-text>
fixture() {
  local dir="$tmp/$1"
  mkdir -p "$dir/notti/src/main/java/com/zeeplabs/notti"
  printf 'package com.zeeplabs.notti\n\ninternal object NottiSdkVersion {\n%s\n}\n' "$2" \
    >"$dir/notti/src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt"
  printf '# Changelog\n\n## [Unreleased]\n' >"$dir/CHANGELOG.md"
  if [[ -n "$3" ]]; then printf '\n%s\n' "$3" >>"$dir/CHANGELOG.md"; fi
  printf '%s\n' "$4" >"$dir/README.md"
  echo "$dir"
}

v='    const val CURRENT = "0.1.0"'

ok="$(fixture ok "$v" "" "Status: pre-release.")"
expect 0 "unreleased only passes" env -u GITHUB_REF "$script" "$ok"

dated="$(fixture dated "$v" "## [0.1.0] - 2026-11-02" "Released.")"
expect 0 "matching dated section passes" env -u GITHUB_REF "$script" "$dated"
expect 0 "tag build with tag, date and README passes" env GITHUB_REF=refs/tags/0.1.0 "$script" "$dated"
expect 0 "branch ref is not a tag build" env GITHUB_REF=refs/heads/main "$script" "$ok"

missing="$(fixture missing "$v" "" "x")"
rm "$missing/notti/src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt"
expect 1 "missing constant file fails" env -u GITHUB_REF "$script" "$missing"

nochangelog="$(fixture nochangelog "$v" "" "x")"
rm "$nochangelog/CHANGELOG.md"
expect 1 "missing CHANGELOG fails" env -u GITHUB_REF "$script" "$nochangelog"

nomatch="$(fixture nomatch '    val current = "0.1.0"' "" "x")"
expect 1 "no constant fails" env -u GITHUB_REF "$script" "$nomatch"

twice="$(fixture twice "$v
$v" "" "x")"
expect 1 "two constants fail" env -u GITHUB_REF "$script" "$twice"

notsemver="$(fixture notsemver '    const val CURRENT = "0.1"' "" "x")"
expect 1 "non-semver fails" env -u GITHUB_REF "$script" "$notsemver"

drift="$(fixture drift "$v" "## [0.2.0] - 2026-11-02" "x")"
expect 1 "CHANGELOG drift fails" env -u GITHUB_REF "$script" "$drift"

baddate="$(fixture baddate "$v" "## [0.1.0] - soon" "x")"
expect 1 "undated section fails" env -u GITHUB_REF "$script" "$baddate"

expect 1 "tag drift fails" env GITHUB_REF=refs/tags/0.1.1 "$script" "$dated"
expect 1 "v-prefixed tag fails" env GITHUB_REF=refs/tags/v0.1.0 "$script" "$dated"
expect 1 "tag build without released section fails" env GITHUB_REF=refs/tags/0.1.0 "$script" "$ok"

tbd="$(fixture tbd "$v" "## [0.1.0] - TBD" "Released.")"
expect 0 "TBD date passes on a branch" env -u GITHUB_REF "$script" "$tbd"
expect 1 "TBD date fails on a tag build" env GITHUB_REF=refs/tags/0.1.0 "$script" "$tbd"

prerelease="$(fixture prerelease "$v" "## [0.1.0] - 2026-11-02" "Status: pre-release.")"
expect 1 "tag build with pre-release README fails" env GITHUB_REF=refs/tags/0.1.0 "$script" "$prerelease"

finish
