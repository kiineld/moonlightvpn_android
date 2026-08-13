package vpn.moonlight.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUserInfoHeaderTest {

    @Test
    fun `parses a complete header`() {
        val info = SubscriptionUserInfoHeader.parse(
            "upload=1024; download=2048; total=107374182400; expire=1788000000",
        )!!

        assertEquals(1024L, info.uploadBytes)
        assertEquals(2048L, info.downloadBytes)
        assertEquals(107_374_182_400L, info.totalBytes)
        assertEquals(1_788_000_000L, info.expiresAtEpochSeconds)
        assertEquals(3072L, info.usedBytes)
    }

    @Test
    fun `tolerates spacing and casing variation`() {
        val info = SubscriptionUserInfoHeader.parse("UPLOAD=1;download=2;  total=3 ;expire=4")!!
        assertEquals(1L, info.uploadBytes)
        assertEquals(2L, info.downloadBytes)
        assertEquals(3L, info.totalBytes)
        assertEquals(4L, info.expiresAtEpochSeconds)
    }

    @Test
    fun `a partial header leaves the rest unknown rather than zero`() {
        val info = SubscriptionUserInfoHeader.parse("download=500")!!
        assertNull(info.uploadBytes)
        assertNull(info.totalBytes)
        assertNull(info.expiresAtEpochSeconds)
        assertEquals(500L, info.usedBytes)
    }

    @Test
    fun `unparseable values are dropped, not defaulted`() {
        val info = SubscriptionUserInfoHeader.parse("upload=abc; total=100")!!
        assertNull(info.uploadBytes)
        assertEquals(100L, info.totalBytes)
    }

    @Test
    fun `absent or meaningless headers yield null`() {
        assertNull(SubscriptionUserInfoHeader.parse(null))
        assertNull(SubscriptionUserInfoHeader.parse(""))
        assertNull(SubscriptionUserInfoHeader.parse("   "))
        assertNull(SubscriptionUserInfoHeader.parse("garbage"))
        assertNull(SubscriptionUserInfoHeader.parse("=5"))
    }

    @Test
    fun `zero total means unlimited, not exhausted`() {
        val info = SubscriptionUserInfoHeader.parse("download=10; total=0")!!
        assertTrue(info.isUnlimitedTraffic)
        assertNull(info.usedFraction)
    }
}
