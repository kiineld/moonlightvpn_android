package vpn.moonlight.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `an import link only opens the app, it cannot point the tunnel anywhere`() {
        listOf(
            "moonlight://import?url=https://sub.example/abc",
            "moonlight://import/https%3A%2F%2Fsub.example%2Fabc",
            "moonlight://add?sub=https://evil.example/x",
        ).forEach { assertEquals(DeepLink.Open, DeepLinks.parse(it)) }
    }

    @Test
    fun `parses the tunnel actions`() {
        assertEquals(DeepLink.Connect, DeepLinks.parse("moonlight://connect"))
        assertEquals(DeepLink.Connect, DeepLinks.parse("moonlight://start"))
        assertEquals(DeepLink.Disconnect, DeepLinks.parse("moonlight://disconnect"))
        assertEquals(DeepLink.Disconnect, DeepLinks.parse("moonlight://stop"))
        assertEquals(DeepLink.Open, DeepLinks.parse("moonlight://open"))
    }

    @Test
    fun `is case insensitive on the scheme and action`() {
        assertEquals(DeepLink.Connect, DeepLinks.parse("Moonlight://CONNECT"))
    }

    @Test
    fun `ignores links that are not ours`() {
        assertNull(DeepLinks.parse(null))
        assertNull(DeepLinks.parse(""))
        assertNull(DeepLinks.parse("https://example.com"))
        assertNull(DeepLinks.parse("vless://x@h:443"))
        assertNull(DeepLinks.parse("moonlight://unknown-action"))
    }
}
