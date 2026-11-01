package com.zeeplabs.notti

/**
 * Options for [Notti.initialize]. A plain class, not a data class, so no `copy` or `componentN` joins
 * the ABI. A later option is added as a new defaulted constructor parameter, and the previous
 * constructor stays as a hidden (`@Deprecated(level = HIDDEN)`) overload: Kotlin callers compiled
 * against an older version call its synthetic defaults constructor, which would otherwise disappear
 * (AD-018).
 *
 * @property baseUrl the Notti API host, e.g. `https://push.example.invalid`; null uses the Notti
 * SaaS host.
 * @property opensInAppUrls whether the SDK opens the http/https link of a tapped In-App `url` button
 * in the browser, after the click listeners ran. Every valid [Notti.initialize] applies the value it
 * carries. Other links and `deeplink` actions are never opened.
 */
class NottiOptions
    @JvmOverloads
    constructor(
        val baseUrl: String? = null,
        val opensInAppUrls: Boolean = true,
    ) {
        override fun equals(other: Any?): Boolean =
            other is NottiOptions && other.baseUrl == baseUrl && other.opensInAppUrls == opensInAppUrls

        override fun hashCode(): Int = 31 * baseUrl.hashCode() + opensInAppUrls.hashCode()

        override fun toString(): String = "NottiOptions(baseUrl=$baseUrl, opensInAppUrls=$opensInAppUrls)"
    }
