#!/usr/bin/env bash
# Fails when the example apps do not reference a public member of Notti, Notti.User, Notti.Notifications,
# Notti.InAppMessages or NottiBridge (EXR-02). Members come from the ABI dump notti/api/notti.api; a
# get<X>/set<X> pair is the Kotlin property <x>. A member counts as covered when "<Owner>.<member>" appears
# word-bounded in a source under example*/src/main (the example writes calls fully qualified). A getter or
# setter name ("Notti.getLogLevel") also covers its property, for Java sources.
#
# Usage: scripts/check-example-coverage.sh [repo-root]
# Exit: 0 covered, 1 members not referenced, 2 input error (no api file, owner block or example source).
set -euo pipefail

root="${1:-$(cd "$(dirname "$0")/.." && pwd)}"
api="$root/notti/api/notti.api"
# ABI class name and the prefix the example writes.
OWNERS=(
  'com/zeeplabs/notti/Notti:Notti'
  'com/zeeplabs/notti/Notti$User:Notti.User'
  'com/zeeplabs/notti/Notti$Notifications:Notti.Notifications'
  'com/zeeplabs/notti/Notti$InAppMessages:Notti.InAppMessages'
  'com/zeeplabs/notti/NottiBridge:NottiBridge'
)

if [[ ! -f "$api" ]]; then
  echo "error: $api not found" >&2
  exit 2
fi

sources=()
while IFS= read -r f; do sources+=("$f"); done < <(
  find "$root" -path "$root/example*/src/main/*" -type f \( -name '*.kt' -o -name '*.java' \) 2>/dev/null | sort
)
if [[ "${#sources[@]}" == 0 ]]; then
  echo "error: no example source under $root/example*/src/main" >&2
  exit 2
fi

# members <abi-class>: one line per public member; "prop <x>" for a get/set pair, "fun <name>" otherwise.
members() {
  awk -v cls="$1" '
    $0 == "public final class " cls " {" { inside = 1; next }
    inside && $0 == "}" { inside = 0; found = 1; next }
    inside && / fun / && !/ synthetic / {
      name = $0
      sub(/.* fun /, "", name)
      sub(/ .*/, "", name)
      if (name ~ /\$default$/) next
      names[name] = 1
    }
    END {
      if (!found) exit 3
      for (n in names) {
        if (n ~ /^get[A-Z]/) { print "prop " tolower(substr(n, 4, 1)) substr(n, 5); continue }
        if (n ~ /^set[A-Z]/ && (("get" substr(n, 4)) in names)) continue
        print "fun " n
      }
    }
  ' "$api"
}

referenced() {
  local pattern="(^|[^A-Za-z0-9_.])${1//./\\.}([^A-Za-z0-9_]|$)"
  grep -qE "$pattern" "${sources[@]}"
}

missing=()
for entry in "${OWNERS[@]}"; do
  cls="${entry%%:*}"
  prefix="${entry#*:}"
  set +e
  list="$(members "$cls")"
  status=$?
  set -e
  if [[ "$status" != 0 ]]; then
    echo "error: no block for $cls in $api" >&2
    exit 2
  fi
  while read -r kind name; do
    [[ -z "${name:-}" ]] && continue
    if referenced "$prefix.$name"; then continue; fi
    if [[ "$kind" == prop ]]; then
      cap="$(tr '[:lower:]' '[:upper:]' <<<"${name:0:1}")${name:1}"
      if referenced "$prefix.get$cap" || referenced "$prefix.set$cap"; then continue; fi
    fi
    missing+=("$prefix.$name")
  done <<<"$list"
done

if [[ "${#missing[@]}" -gt 0 ]]; then
  echo "error: the example apps do not reference these public members (EXR-02):" >&2
  printf '  %s\n' "${missing[@]}" | sort >&2
  exit 1
fi
echo "example coverage ok: every public member is referenced"
