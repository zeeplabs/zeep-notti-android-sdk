# shellcheck shell=bash
# Shared helpers for the script self-tests. Sourced, not executed.
failures=0

# expect <exit-code> <description> <command...>
expect() {
  local want="$1" desc="$2"
  shift 2
  local out got
  set +e
  out="$("$@" 2>&1)"
  got=$?
  set -e
  if [[ "$got" == "$want" ]]; then
    echo "ok   - $desc"
  else
    echo "FAIL - $desc (expected exit $want, got $got)"
    printf '       %s\n' "${out//$'\n'/$'\n'       }"
    failures=$((failures + 1))
  fi
}

finish() {
  if [[ "$failures" -gt 0 ]]; then
    echo "$failures failure(s)"
    exit 1
  fi
}
