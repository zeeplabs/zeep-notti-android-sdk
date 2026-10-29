# Core Port Traceability

**Spec**: `.specs/features/core-port/spec.md`
**Design**: `.specs/features/core-port/design.md`
**Status**: In progress (T2 storage contract; the rest is filled by T14)

---

## Storage contract

Every key `NottiDeviceStore` reads or writes in `notti_prefs`, with the `SharedPreferences` type of its `put*`/`get*` calls and the read default, taken from the ported file (identical to RN v0.8.0 `NottiDeviceStore.kt`). A continuity fixture written by RN 0.8.0 must read back with these exact types: a type change throws `ClassCastException` on upgrade.

Conventions: "`-1L` → null" means the getter reads `getLong(key, -1L).takeIf { it >= 0 }` and the setter writes `value ?: -1L` (the key stays present after a clear). String setters write `putString(key, null)`, which removes the key. Writes use `apply()` unless the Write column says `commit()`.

| Key constant | Key | Type | Read default | Write |
| --- | --- | --- | --- | --- |
| `KEY_DEVICE_ID` | `notti_device_id` | String | `null` | apply |
| `KEY_LAST_TOKEN` | `notti_last_token` | String | `null` | apply |
| `KEY_EXTERNAL_USER_ID` | `notti_external_user_id` | String | `null` | apply |
| `KEY_SUBSCRIBED` | `notti_subscribed` | Boolean | `false` (`getSubscribedOrNull`: `null` when absent, via `contains`) | apply |
| `TAG_KEY_PREFIX` | `notti_tag_<key>` | String (one key per tag; read through `getAll()` filtered by prefix) | no tags | apply (`setTags` removes old tag keys, puts new ones, one editor) |
| `KEY_APP_VERSION` | `notti_app_version` | String | `null` | apply; removed by `migrateProfileFieldAckIfNeeded` and `clearProfileFieldAcks` |
| `KEY_FIRST_SESSION_AT` | `notti_first_session_at_ms` | Long | `-1L` → null | apply |
| `KEY_LAST_SESSION_AT` | `notti_last_session_at_ms` | Long | `-1L` → null | apply |
| `KEY_SESSION_COUNT` | `notti_session_count` | **Int** | `0` | apply |
| `KEY_SESSION_TIME_MS` | `notti_session_time_ms` | Long | `0L` | apply |
| `KEY_SESSION_STARTED_AT` | `notti_session_started_at_ms` | Long | `-1L` → null | apply |
| `KEY_LOCATION_SHARING_ENABLED` | `notti_location_sharing_enabled` | Boolean | `false` | **commit()** (LGPD opt-out) |
| `KEY_PENDING_COUNTRY_CLEAR` | `notti_pending_country_clear` | Boolean | `false` | **commit()** |
| `KEY_PENDING_EXTERNAL_USER_ID_CLEAR` | `notti_pending_external_user_id_clear` | Boolean | `false` | **commit()** |
| `KEY_LAST_SYNCED_COUNTRY` | `notti_last_synced_country` | String | `null` | apply |
| `KEY_LAST_FOREGROUND_AT` | `notti_last_foreground_at_ms` | Long | `-1L` → null | apply |
| `KEY_LAST_SYNCED_DEVICE_OS` | `notti_last_synced_device_os` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_DEVICE_MODEL` | `notti_last_synced_device_model` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_SDK_VERSION` | `notti_last_synced_sdk_version` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_TIMEZONE_ID` | `notti_last_synced_timezone_id` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_LANGUAGE` | `notti_last_synced_language` | String | `null` | apply; removed by both (see `notti_app_version`) |
| `KEY_LAST_SYNCED_PERMISSION_STATUS` | `notti_last_synced_permission_status` | String | `null` | apply; removed by `clearProfileFieldAcks` only |
| `KEY_LAST_UNSUBSCRIBED_AT_MS` | `notti_last_unsubscribed_at_ms` | Long | `-1L` → null | apply |
| `KEY_EMAIL` | `notti_email` | String | `null` | **commit()** (LGPD clear) |
| `KEY_PHONE` | `notti_phone` | String | `null` | **commit()** |
| `KEY_LAST_SYNCED_EMAIL` | `notti_last_synced_email` | String | `null` | **commit()** |
| `KEY_LAST_SYNCED_PHONE` | `notti_last_synced_phone` | String | `null` | **commit()** |
| `KEY_PERMISSION_REQUESTED` | `notti_permission_requested` | Boolean | `false` | apply |
| `KEY_PENDING_UNSUBSCRIBE_AT_MS` | `notti_pending_unsubscribe_at_ms` | Long | `-1L` → null | apply |
| `KEY_PENDING_PERMISSION_UNSUBSCRIBE_AT_MS` | `notti_pending_permission_unsubscribe_at_ms` | Long | `-1L` → null | apply |
| `KEY_PROFILE_FIELD_ACK_MIGRATED` | `notti_profile_field_ack_migrated` | Boolean | `false` | apply (set `true` once by `migrateProfileFieldAckIfNeeded`, in the same editor as its removals) |

Design note: the design's data-model table groups `notti_session_count` with the Long keys; it is an **Int** (`putInt`/`getInt`), the only Int key in the store.
