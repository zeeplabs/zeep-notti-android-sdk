#!/usr/bin/env bash
# Self-test for scripts/check-example-coverage.sh (EXR-02).
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
script="$here/../check-example-coverage.sh"
# shellcheck source=SCRIPTDIR/lib.sh
source "$here/lib.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# A small ABI dump with every owner block: functions, a get/set property, a set without get, an overload and
# the synthetic default that must be skipped.
API='public final class com/zeeplabs/notti/Notti {
	public static final field INSTANCE Lcom/zeeplabs/notti/Notti;
	public static final fun getLogLevel ()Lcom/zeeplabs/notti/NottiLogLevel;
	public static final fun initialize (Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;)V
	public static final fun initialize (Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Lcom/zeeplabs/notti/NottiOptions;)V
	public static synthetic fun initialize$default (Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Lcom/zeeplabs/notti/NottiOptions;ILjava/lang/Object;)V
	public static final fun setLogLevel (Lcom/zeeplabs/notti/NottiLogLevel;)V
	public static final fun setSubscription (Z)V
}

public final class com/zeeplabs/notti/Notti$InAppMessages {
	public static final fun addClickListener (Lcom/zeeplabs/notti/NottiInAppClickListener;)Lcom/zeeplabs/notti/NottiSubscription;
}

public final class com/zeeplabs/notti/Notti$Notifications {
	public static final fun takeInitialClick ()Lcom/zeeplabs/notti/NottiNotification;
}

public final class com/zeeplabs/notti/Notti$User {
	public static final fun addTag (Ljava/lang/String;Ljava/lang/String;)V
	public static final fun addTags (Ljava/util/Map;)V
}

public final class com/zeeplabs/notti/NottiBridge {
	public static final fun onNewToken (Ljava/lang/String;)V
}'

FULL='Notti.logLevel = NottiLogLevel.DEBUG
Notti.initialize(this, appId, clientKey)
Notti.setSubscription(true)
Notti.InAppMessages.addClickListener { }
Notti.Notifications.takeInitialClick()
Notti.User.addTag("k", "v")
Notti.User.addTags(mapOf())'

BRIDGE='NottiBridge.onNewToken(token)'

# repo <name> <api|-> <example-source|-> [manual-fcm-source]
repo() {
  local dir="$tmp/$1"
  mkdir -p "$dir/notti/api" "$dir/example/src/main/java" "$dir/example-manual-fcm/src/main/java"
  if [[ "$2" != - ]]; then printf '%s\n' "$2" >"$dir/notti/api/notti.api"; fi
  if [[ "$3" != - ]]; then printf '%s\n' "$3" >"$dir/example/src/main/java/Main.kt"; fi
  if [[ -n "${4:-}" ]]; then printf '%s\n' "$4" >"$dir/example-manual-fcm/src/main/java/Service.kt"; fi
  echo "$dir"
}

expect 0 "full coverage passes" "$script" "$(repo full "$API" "$FULL" "$BRIDGE")"
expect 1 "a missing function fails" "$script" "$(repo nobridge "$API" "$FULL")"
expect 1 "a missing property fails" "$script" "$(repo noprop "$API" "${FULL//Notti.logLevel = NottiLogLevel.DEBUG/}" "$BRIDGE")"
expect 0 "a property referenced by its getter name passes" "$script" \
  "$(repo getter "$API" "${FULL//Notti.logLevel = NottiLogLevel.DEBUG/Notti.getLogLevel()}" "$BRIDGE")"
expect 1 "a set function without a getter is a function, not a property" "$script" \
  "$(repo setfun "$API" "${FULL//Notti.setSubscription(true)/Notti.subscription = true}" "$BRIDGE")"
expect 1 "a prefix match does not count (addTags for addTag)" "$script" \
  "$(repo prefix "$API" "${FULL//Notti.User.addTag(\"k\", \"v\")/}" "$BRIDGE")"
expect 1 "another owner does not count (Notti.addTag for Notti.User.addTag)" "$script" \
  "$(repo owner "$API" "${FULL//Notti.User.addTag(/Notti.addTag(}" "$BRIDGE")"
expect 1 "a longer receiver does not count (MyNotti.setSubscription)" "$script" \
  "$(repo receiver "$API" "${FULL//Notti.setSubscription/MyNotti.setSubscription}" "$BRIDGE")"
expect 2 "a missing owner block is an input error" "$script" \
  "$(repo noblock "${API//com\/zeeplabs\/notti\/NottiBridge/com\/zeeplabs\/notti\/Other}" "$FULL" "$BRIDGE")"
expect 2 "a missing api file is an input error" "$script" "$(repo noapi - "$FULL" "$BRIDGE")"
expect 2 "no example source is an input error" "$script" "$(repo nosrc "$API" -)"
expect 0 "the repository passes" "$script"

finish
