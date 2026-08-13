package vpn.moonlight.ui.split

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vpn.moonlight.data.model.InstalledApp

class AppRowFilterTest {

    private val telegram = AppRow(
        InstalledApp("org.telegram.messenger", "Telegram", isSystem = false),
        isSelected = false,
    )

    @Test
    fun `an empty query matches everything`() {
        assertTrue(telegram.matches(""))
        assertTrue(telegram.matches("   "))
    }

    @Test
    fun `matches the label regardless of case`() {
        assertTrue(telegram.matches("tele"))
        assertTrue(telegram.matches("TELEGRAM"))
    }

    @Test
    fun `matches the package too, for apps whose label does not say much`() {
        assertTrue(telegram.matches("org.tele"))
        assertTrue(telegram.matches("messenger"))
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertTrue(telegram.matches("  telegram  "))
    }

    @Test
    fun `a query that matches neither is excluded`() {
        assertFalse(telegram.matches("spotify"))
    }
}
