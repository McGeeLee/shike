package com.gee.eatapp.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateClientTest {
    @Test
    fun newerVersionComparesEachNumericPart() {
        assertTrue(isNewerVersion("2.1.0", "2.0.9"))
        assertTrue(isNewerVersion("v3.0", "2.9.9"))
        assertFalse(isNewerVersion("2.0.0", "2.0"))
        assertFalse(isNewerVersion("1.9.9", "2.0.0"))
    }

    @Test
    fun prereleaseDoesNotReplaceSameStableVersion() {
        assertFalse(isNewerVersion("2.1.0-beta.1", "2.1.0"))
        assertTrue(isNewerVersion("2.1.0", "2.1.0-rc.1"))
    }

    @Test
    fun malformedVersionIsIgnored() {
        assertFalse(isNewerVersion("latest", "2.0.0"))
        assertFalse(isNewerVersion("2.1.0", "unknown"))
    }

    @Test
    fun checksumParserAcceptsSha256sumFormatOnly() {
        val checksum = "a".repeat(64)
        assertEquals(checksum, parseSha256("$checksum  shike-v2.1.0.apk\n"))
        assertNull(parseSha256("not-a-checksum"))
    }
}
