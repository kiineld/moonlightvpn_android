package vpn.moonlight.data.util

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {

    @Test
    fun `precision shortens as the number grows, matching the design`() {
        // "1,24 GB" — two decimals below ten
        val small = Formatters.formatSize(1_331_439_861L, Locale.US)
        assertEquals(ByteUnit.Gigabytes, small.unit)
        assertEquals("1.24", small.text)

        // "24,8 GB" — one decimal below a hundred
        val medium = Formatters.formatSize(26_630_000_000L, Locale.US)
        assertEquals(ByteUnit.Gigabytes, medium.unit)
        assertEquals("24.8", medium.text)

        // "100 GB" — none above
        val large = Formatters.formatSize(107_374_182_400L, Locale.US)
        assertEquals(ByteUnit.Gigabytes, large.unit)
        assertEquals("100", large.text)
    }

    @Test
    fun `uses the locale decimal separator`() {
        val russian = Formatters.formatSize(1_331_439_861L, Locale.forLanguageTag("ru"))
        assertEquals("1,24", russian.text)
    }

    @Test
    fun `scales up through the units and never below zero`() {
        assertEquals(ByteUnit.Bytes, Formatters.formatSize(512L, Locale.US).unit)
        assertEquals(ByteUnit.Kilobytes, Formatters.formatSize(2048L, Locale.US).unit)
        assertEquals(ByteUnit.Megabytes, Formatters.formatSize(5L * 1024 * 1024, Locale.US).unit)
        assertEquals(ByteUnit.Terabytes, Formatters.formatSize(3L * 1024 * 1024 * 1024 * 1024, Locale.US).unit)

        val negative = Formatters.formatSize(-5L, Locale.US)
        assertEquals(ByteUnit.Bytes, negative.unit)
        assertEquals("0", negative.text)
    }

    @Test
    fun `duration matches the dial timer format`() {
        assertEquals("00:00:00", Formatters.formatDuration(0))
        assertEquals("00:12:47", Formatters.formatDuration(767))
        assertEquals("01:00:00", Formatters.formatDuration(3600))
        assertEquals("00:00:00", Formatters.formatDuration(-10))
    }

    @Test
    fun `hours are not wrapped at a day, because sessions run longer`() {
        assertEquals("30:00:00", Formatters.formatDuration(108_000))
    }

    @Test
    fun `elapsed seconds floor at zero for a clock that moved backwards`() {
        assertEquals(5L, Formatters.elapsedSeconds(1_000L, 6_400L))
        assertEquals(0L, Formatters.elapsedSeconds(10_000L, 5_000L))
    }

    @Test
    fun `shortening a url keeps the host and the tail`() {
        assertEquals(
            "sub.moonlight.vpn/8f31c2a7",
            Formatters.shortenUrl("https://sub.moonlight.vpn/8f31c2a7"),
        )
        assertEquals(
            "sub.moonlight.vpn/…cdef1234",
            Formatters.shortenUrl("https://sub.moonlight.vpn/api/sub/abcdef1234"),
        )
        assertEquals("sub.moonlight.vpn", Formatters.shortenUrl("https://sub.moonlight.vpn"))
    }
}
