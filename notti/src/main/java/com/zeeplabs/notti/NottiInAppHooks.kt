// Ported from react-native-notti v0.8.0 (291d5a7): android/src/main/java/com/notti/NottiInAppCoordinator.kt lines 41-55 (interface only)
package com.zeeplabs.notti

/**
 * What [NottiCore] tells In-App. Network and foreground/background reach the
 * coordinator straight from their observers, which also run with no core.
 */
internal interface NottiInAppHooks {
    /** A foreground session was opened (the process session gate flipped). */
    fun onSessionStarted() = Unit

    /** The device registration succeeded and the push event queue was flushed. */
    fun onRegistered() = Unit

    companion object {
        val None: NottiInAppHooks = object : NottiInAppHooks {}
    }
}
