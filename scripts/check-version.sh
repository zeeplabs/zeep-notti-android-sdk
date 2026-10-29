#!/usr/bin/env bash
# Fails when the SDK version drifts between NottiSdkVersion.CURRENT, the top released CHANGELOG section
# and (on tag builds) the git tag (PKG-07). The Maven version cannot drift: notti/build.gradle.kts reads the
# same constant.
#
# The top released section is the first `## [x.y.z] - <date>` heading; `## [Unreleased]` is skipped. Its date
# is `YYYY-MM-DD`, or `TBD` until release day. With no released section yet, only the constant is checked,
# except on a tag build, which needs a dated section, a tag equal to the constant (`X.Y.Z`, no `v`) and a
# README that no longer says pre-release.
#
# Usage: scripts/check-version.sh [repo-root]
# Reads GITHUB_REF (e.g. refs/tags/0.1.0) when set.
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
kotlin_file="$root/notti/src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt"
changelog="$root/CHANGELOG.md"
readme="$root/README.md"

for f in "$kotlin_file" "$changelog" "$readme"; do
  if [[ ! -f "$f" ]]; then
    echo "error: $f not found" >&2
    exit 1
  fi
done

matches="$(sed -nE 's/^[[:space:]]*const val CURRENT = "([^"]+)".*/\1/p' "$kotlin_file")"
count="$(grep -c . <<<"$matches" || true)"
if [[ "$count" != "1" ]]; then
  echo "error: expected exactly one NottiSdkVersion.CURRENT in $kotlin_file, found $count" >&2
  exit 1
fi
version="$matches"

if [[ ! "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "error: NottiSdkVersion.CURRENT=$version is not semver" >&2
  exit 1
fi

top_heading="$(grep -m1 -E '^## \[[0-9]+\.[0-9]+\.[0-9]+[^]]*\]' "$changelog" || true)"
changelog_date=""
if [[ -n "$top_heading" ]]; then
  changelog_version="$(sed -E 's/^## \[([^]]+)\].*/\1/' <<<"$top_heading")"
  changelog_date="$(sed -nE 's/^## \[[^]]+\][[:space:]]+-[[:space:]]+(.*[^[:space:]])[[:space:]]*$/\1/p' <<<"$top_heading")"
  if [[ "$version" != "$changelog_version" ]]; then
    echo "error: version drift: NottiSdkVersion.CURRENT=$version, top CHANGELOG section=$changelog_version" >&2
    exit 1
  fi
  if [[ ! "$changelog_date" =~ ^([0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])|TBD)$ ]]; then
    echo "error: top CHANGELOG section '$top_heading' needs a date (YYYY-MM-DD) or TBD" >&2
    exit 1
  fi
fi

ref="${GITHUB_REF:-}"
if [[ "$ref" == refs/tags/* ]]; then
  tag="${ref#refs/tags/}"
  if [[ "$tag" != "$version" ]]; then
    echo "error: version drift: tag=$tag, NottiSdkVersion.CURRENT=$version" >&2
    exit 1
  fi
  if [[ -z "$top_heading" || "$changelog_date" == "TBD" ]]; then
    echo "error: tag $tag needs a dated CHANGELOG section '## [$version] - YYYY-MM-DD'" >&2
    exit 1
  fi
  if grep -qiE 'pre-release|release candidate' "$readme"; then
    echo "error: tag $tag but README.md still says pre-release or release candidate (flip the banner first)" >&2
    exit 1
  fi
fi

echo "version ok: $version"
