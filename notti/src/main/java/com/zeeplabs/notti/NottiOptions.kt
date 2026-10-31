package com.zeeplabs.notti

/**
 * Options for [Notti.initialize]. A plain class, not a data class: later options are added as new
 * defaulted constructor parameters without breaking the ABI.
 *
 * @property baseUrl the Notti API host, e.g. `https://push.example.invalid`; null uses the Notti
 * SaaS host.
 */
class NottiOptions
    @JvmOverloads
    constructor(
        val baseUrl: String? = null,
    ) {
        override fun equals(other: Any?): Boolean = other is NottiOptions && other.baseUrl == baseUrl

        override fun hashCode(): Int = baseUrl.hashCode()

        override fun toString(): String = "NottiOptions(baseUrl=$baseUrl)"
    }
