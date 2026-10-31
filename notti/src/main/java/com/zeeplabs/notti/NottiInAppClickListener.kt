package com.zeeplabs.notti

/** Receives an In-App message tap; called on the main thread, before the message closes. */
fun interface NottiInAppClickListener {
    fun onClick(click: NottiInAppClick)
}
