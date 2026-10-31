package com.zeeplabs.notti

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/** Opens the `url` action of a tapped In-App message (AD-006); a port of the coordinator, like the push-permission one. */
internal interface InAppUrlOpener {
    /** `NottiOptions.opensInAppUrls`; every valid `initialize` sets it. */
    var enabled: Boolean

    /**
     * True when a tap on a `url` action with [value] opens it: [enabled] and [value] accepted. A value refused
     * while enabled logs one fixed debug line; nothing is logged while disabled.
     */
    fun willOpen(value: String): Boolean

    /** Starts one `ACTION_VIEW` for [value], on the main thread; a failure logs one fixed warn line, no retry. */
    fun open(value: String)
}

/**
 * The only SDK code that opens a URL (`scripts/check-no-url-open.sh` allowlists this file alone). Accepts an
 * http/https value with a host and no whitespace or control character, checked before parsing; the `Uri` is
 * built from the value unchanged. Opens from the resumed Activity, else from the application context in a new
 * task. Never logs the value.
 */
internal class NottiInAppUrlOpener(
    private val context: Context,
    private val currentActivity: () -> Activity?,
) : InAppUrlOpener {
    @Volatile
    override var enabled: Boolean = true

    override fun willOpen(value: String): Boolean {
        if (!enabled) return false
        if (accepts(value)) return true
        NottiLogger.debug("Notti.inapp: url action not opened")
        return false
    }

    override fun open(value: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, value.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
            val activity = currentActivity()
            if (activity != null) {
                activity.startActivity(intent)
            } else {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        } catch (e: Exception) {
            NottiLogger.warn("Notti.inapp: url open failed - ${e.javaClass.simpleName}")
        }
    }

    companion object {
        /** http/https (any case), a non-empty host, no whitespace or control character. */
        fun accepts(value: String): Boolean {
            if (value.isEmpty() || value.any { it.isWhitespace() || it.isISOControl() }) return false
            val uri = value.toUri()
            val scheme = uri.scheme?.lowercase() ?: return false
            return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
        }
    }
}
