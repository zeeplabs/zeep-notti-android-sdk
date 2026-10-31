#!/usr/bin/env bash
# Signed dry run of the release publication (EXR-17): signs with a throwaway key, publishes to build/repo and
# checks that every artifact has a valid signature and that the POM has what Maven Central requires. Nothing is
# uploaded. The key lives in a temporary GNUPGHOME, is never printed and is deleted on exit.
#
# Usage: scripts/check-release-artifacts.sh [repo-root]
# Exit: 0 ok, 1 a check failed, 2 gpg missing or the build failed.
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"

if ! command -v gpg >/dev/null 2>&1; then
  echo "error: gpg not found; install GnuPG (macOS: brew install gnupg; Debian/Ubuntu: apt-get install gnupg)" >&2
  exit 2
fi

GNUPGHOME="$(mktemp -d)"
export GNUPGHOME
trap 'gpgconf --kill all >/dev/null 2>&1 || true; rm -rf "$GNUPGHOME"' EXIT
chmod 700 "$GNUPGHOME"

gpg --batch --quiet --pinentry-mode loopback --passphrase '' \
  --quick-generate-key 'Notti Release Check <release-check@example.invalid>' rsa3072 sign 1d
key="$(gpg --batch --armor --export-secret-keys 'release-check@example.invalid')"

version="$(sed -nE 's/^[[:space:]]*const val CURRENT = "([^"]+)".*/\1/p' \
  "$root/notti/src/main/java/com/zeeplabs/notti/NottiSdkVersion.kt")"
dir="$root/build/repo/com/zeeplabs/notti-android/$version"
rm -rf "$dir"

if ! (cd "$root" && ORG_GRADLE_PROJECT_signingInMemoryKey="$key" \
  ./gradlew --console=plain :notti:publishAllPublicationsToBuildRepository); then
  echo "error: the signed publish failed" >&2
  exit 2
fi

failures=0
for f in "notti-android-$version.aar" "notti-android-$version.pom" "notti-android-$version-sources.jar" \
  "notti-android-$version-javadoc.jar" "notti-android-$version.module"; do
  if [[ ! -f "$dir/$f" ]]; then
    echo "FAIL - $f missing"
    failures=$((failures + 1))
  elif [[ ! -f "$dir/$f.asc" ]]; then
    echo "FAIL - $f.asc missing (not signed)"
    failures=$((failures + 1))
  elif gpg --batch --quiet --verify "$dir/$f.asc" "$dir/$f" 2>/dev/null; then
    echo "ok   - $f signature"
  else
    echo "FAIL - $f signature does not verify"
    failures=$((failures + 1))
  fi
done

pom="$dir/notti-android-$version.pom"
if [[ -f "$pom" ]]; then
  # Fields Maven Central requires: one non-empty element per path (first match, whitespace trimmed).
  for path in "project/name" "project/description" "project/url" "project/licenses/license/name" \
    "project/developers/developer/id" "project/scm/url"; do
    leaf="${path##*/}"
    parent="${path%/*}"
    parent="${parent##*/}"
    value="$(awk -v parent="$parent" -v leaf="$leaf" '
      { line = $0 }
      line ~ "<" parent "[ >]" { inside = 1 }
      inside && match(line, "<" leaf ">[^<]*</" leaf ">") {
        v = substr(line, RSTART + length(leaf) + 2, RLENGTH - 2 * length(leaf) - 5)
        gsub(/^[ \t]+|[ \t]+$/, "", v)
        print v
        exit
      }
      line ~ "</" parent ">" { inside = 0 }
    ' "$pom")"
    if [[ -n "$value" ]]; then
      echo "ok   - POM $path"
    else
      echo "FAIL - POM $path missing or empty"
      failures=$((failures + 1))
    fi
  done
fi

if [[ "$failures" -gt 0 ]]; then
  echo "$failures failure(s)"
  exit 1
fi
echo "release artifacts ok: com.zeeplabs:notti-android:$version signed and complete"
