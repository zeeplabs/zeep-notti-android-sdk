package com.zeeplabs.notti

/** Receives the answer to a notification-permission request; called once, on the main thread. */
fun interface NottiPermissionCallback {
    fun onResult(granted: Boolean)
}
