package com.zeeplabs.notti

import org.junit.Assert.assertTrue
import org.junit.Test

class NottiSdkVersionTest {
    @Test
    fun currentIsSemver() {
        val semver = Regex("""^\d+\.\d+\.\d+(-[0-9A-Za-z.-]+)?$""")
        assertTrue("not semver: ${NottiSdkVersion.CURRENT}", semver.matches(NottiSdkVersion.CURRENT))
    }
}
