// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiInitProvider.kt lines 1-99 (NottiInitProvider only)
@file:Suppress(
    "ktlint:standard:function-signature",
    "ktlint:standard:trailing-comma-on-declaration-site",
)

package com.zeeplabs.notti

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri

/**
 * Zero-integration-code process-start hook: a manifest-declared
 * [ContentProvider]'s `onCreate` runs before `Application.onCreate` finishes
 * and, crucially, before any Activity is created - which is what the
 * cold-start notification-click path (spec P3-AC7) needs, since
 * [NottiModule] is a lazy TurboModule that does not exist yet when the
 * launching Activity reads its intent. Same mechanism `androidx.startup` and
 * `react-native-firebase` use.
 *
 * It only installs the SDK's process-start hooks - the notification-click
 * detector, the app-foreground observer that resumes registration (spec
 * SDK-05), and the network-reconnect observer that flushes the offline event
 * queue (T4) - and seeds the process-wide event store these sites write to.
 * No SDK work, no network, nothing that could slow or break host app startup.
 * The hooks live in [NottiProcessHooks] (public-api T2), shared with `Notti.initialize`.
 */
internal class NottiInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application
        if (application == null) {
            NottiLogger.error("Notti: no Application context at startup - notification clicks from a cold start will not be detected")
            return false
        }
        NottiProcessHooks.installOnce(application)
        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? = null

    override fun getType(uri: Uri): String? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0
}
