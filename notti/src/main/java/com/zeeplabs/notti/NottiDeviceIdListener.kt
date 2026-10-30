package com.zeeplabs.notti

/** Receives the device id the Notti backend assigned; called on the main thread. */
fun interface NottiDeviceIdListener {
    fun onDeviceIdChanged(deviceId: String)
}
