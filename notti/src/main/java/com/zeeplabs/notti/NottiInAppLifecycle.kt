package com.zeeplabs.notti

/**
 * What the process observers tell In-App: the four calls RN's `NottiForegroundObserver` and
 * `NottiNetworkObserver` make on `NottiInAppCoordinator`. The observers reach it through
 * `NottiRuntime.inAppLifecycle`, null until `in-app-messages` plugs its coordinator in, so they
 * compile and run with no In-App engine.
 */
internal interface NottiInAppLifecycle {
    /** The app came to the foreground (process `onStart`). */
    fun onAppForegrounded()

    /** The process went to the background: the In-App session ends (called also with no core live). */
    fun onSessionEnded()

    /** The app went to the background (process `onStop`, right after [onSessionEnded]). */
    fun onAppBackgrounded()

    /** The default network became available again. */
    fun onNetworkAvailable()
}
