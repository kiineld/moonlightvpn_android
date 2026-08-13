package vpn.moonlight.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUserInfoTest {

    private val now = 1_787_000_000L

    @Test
    fun `days left rounds up so a part-day is not reported as zero`() {
        val sixHoursLeft = SubscriptionUserInfo(expiresAtEpochSeconds = now + 6 * 3600)
        assertEquals(1, sixHoursLeft.daysLeft(now))

        val twelveDays = SubscriptionUserInfo(expiresAtEpochSeconds = now + 12 * 86_400)
        assertEquals(12, twelveDays.daysLeft(now))
    }

    @Test
    fun `an expired subscription reports zero days and reads as expired`() {
        val info = SubscriptionUserInfo(expiresAtEpochSeconds = now - 60)
        assertEquals(0, info.daysLeft(now))
        assertTrue(info.isExpired(now))
    }

    @Test
    fun `a perpetual subscription has no day count and never expires`() {
        listOf(null, 0L).forEach { expiry ->
            val info = SubscriptionUserInfo(expiresAtEpochSeconds = expiry)
            assertTrue(info.isPerpetual)
            assertNull(info.daysLeft(now))
            assertFalse(info.isExpired(now))
        }
    }

    @Test
    fun `used bytes sums both directions but stays null when neither is known`() {
        assertEquals(300L, SubscriptionUserInfo(uploadBytes = 100, downloadBytes = 200).usedBytes)
        assertEquals(100L, SubscriptionUserInfo(uploadBytes = 100).usedBytes)
        assertNull(SubscriptionUserInfo(totalBytes = 500).usedBytes)
    }

    @Test
    fun `used fraction needs both halves and clamps to one`() {
        assertEquals(
            0.25f,
            SubscriptionUserInfo(downloadBytes = 25, totalBytes = 100).usedFraction!!,
            0.0001f,
        )
        assertEquals(
            1f,
            SubscriptionUserInfo(downloadBytes = 500, totalBytes = 100).usedFraction!!,
            0.0001f,
        )
        assertNull(SubscriptionUserInfo(downloadBytes = 25).usedFraction)
        assertNull(SubscriptionUserInfo(totalBytes = 100).usedFraction)
    }
}
