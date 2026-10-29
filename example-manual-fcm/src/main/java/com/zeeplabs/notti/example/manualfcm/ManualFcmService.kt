package com.zeeplabs.notti.example.manualfcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.zeeplabs.notti.NottiBridge

/** The host's own FCM service: its own handling would go here, then each callback is forwarded to Notti. */
class ManualFcmService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        NottiBridge.onNewToken(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        NottiBridge.onMessageReceived(remoteMessage)
    }
}
