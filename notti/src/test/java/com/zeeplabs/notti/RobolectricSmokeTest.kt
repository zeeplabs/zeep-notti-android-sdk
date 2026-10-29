package com.zeeplabs.notti

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Proves Robolectric is on the test classpath: the plain android.jar Bundle stub throws (PKG-11). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RobolectricSmokeTest {
    @Test
    fun bundleRoundTrip() {
        val bundle = Bundle()
        bundle.putString("key", "fixture")
        assertEquals("fixture", bundle.getString("key"))
    }
}
