// Moved from NottiInitProvider.onCreate (ported from react-native-notti v0.8.0 (291d5a7):
// android/src/main/java/com/notti/NottiInitProvider.kt lines 1-99); the body is unchanged, only relocated
// so that `Notti.initialize` can install the same hooks when the host removed the provider (API-13).
package com.zeeplabs.notti

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ProcessLifecycleOwner
import java.lang.ref.WeakReference

/**
 * The SDK's process-start hooks - the notification-click detector, the app-foreground observer that
 * resumes registration (spec SDK-05), and the network-reconnect observer that flushes the offline
 * event queue - plus the process-wide runtime these sites write to. Installed by [NottiInitProvider]
 * at process start and by `Notti.initialize`; whichever runs first installs, the other is a no-op.
 * No SDK work, no network, nothing that could slow or break host app startup.
 */
internal object NottiProcessHooks {
    /**
     * The Application the hooks are installed on. One per process in an app; keyed to the instance
     * (not a bare flag) because every hook is registered on that Application and its services.
     */
    private var installedOn: WeakReference<Application>? = null

    /** Installs the In-App engine. Test seam; production never reassigns it. */
    @Volatile
    internal var inAppInstaller: (Application) -> Unit = { NottiInAppSetup.install(it) }

    /** Installs every hook once per [application]; each step fails alone, logged by class (SDK-03). */
    @Synchronized
    fun installOnce(application: Application) {
        if (installedOn?.get() === application) return
        installedOn = WeakReference(application)

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

        // In-App: installed before any Activity so the tracker sees the first
        // resume. Process-wide, it outlives every core.
        try {
            inAppInstaller(application)
        } catch (t: Throwable) {
            NottiLogger.error("Notti: could not set up In-App messages - ${t.javaClass.simpleName}")
        }

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
    }
}
