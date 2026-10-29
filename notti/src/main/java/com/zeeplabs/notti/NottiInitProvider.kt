// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiInitProvider.kt lines 1-99 (NottiInitProvider only)
@file:Suppress(
    "ktlint:standard:function-signature",
    "ktlint:standard:trailing-comma-on-declaration-site",
)

package com.zeeplabs.notti

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner

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
 */
internal class NottiInitProvider : ContentProvider() {
    override fun onCreate(): Boolean {
        val application = context?.applicationContext as? Application
        if (application == null) {
            NottiLogger.error("Notti: no Application context at startup - notification clicks from a cold start will not be detected")
            return false
        }
        try {
            NottiActivityLifecycleListener.registerOnce(application)
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not register notification click detection - ${t.javaClass.simpleName}")
        }

        // Seed the process-wide event store here: the detection sites and the
        // network observer below fire before the lazy module exists, and this is
        // the earliest point a Context is available to back the store.
        try {
            NottiRuntime.get(application)
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not create the runtime - ${t.javaClass.simpleName}")
        }

        // In-App: `in-app-messages` installs NottiInAppSetup here.

        // Flush the queued event store when connectivity returns - the reporting
        // path for events enqueued while offline (T4). Registered once per
        // process; the observer itself swallows any rejection (SDK-03).
        try {
            val connectivityManager =
                application.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            if (connectivityManager != null) {
                NottiNetworkObserver(connectivityManager).register()
            }
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not register the network observer - ${t.javaClass.simpleName}")
        }

        // ProcessLifecycleOwner is itself set up by androidx.startup's own
        // ContentProvider, whose creation order relative to this one is not
        // defined; posting to the main looper puts this after every provider and
        // after Application.onCreate, and still ahead of the first Activity start.
        try {
            Handler(Looper.getMainLooper()).post {
                try {
                    ProcessLifecycleOwner.get().lifecycle.addObserver(NottiForegroundObserver)
                } catch (t: Throwable) {
                    NottiLogger.error("Notti: could not observe app foreground - ${t.javaClass.simpleName}")
                }
            }
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not observe app foreground - ${t.javaClass.simpleName}")
        }
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
