package com.zeeplabs.notti

/** Receives a notification; called on the main thread. */
fun interface NottiNotificationListener {
    fun onNotification(notification: NottiNotification)
}
