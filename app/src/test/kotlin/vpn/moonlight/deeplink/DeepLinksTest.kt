package vpn.moonlight.deeplink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinksTest {

    @Test
    fun `imports a subscription from the url parameter`() {
        val link = DeepLinks.parse("moonlight://import?url=https://sub.example/abc")
        assertEquals(DeepLink.ImportSubscription("https://sub.example/abc"), link)
    }

    @Test
    fun `accepts a percent-encoded url, which is how a bot will send it`() {
        val link = DeepLinks.parse(
            "moonlight://import?url=https%3A%2F%2Fsub.example%2FaBcD1234EfGh5678",
        )
        assertEquals(
            DeepLink.ImportSubscription("https://sub.example/aBcD1234EfGh5678"),
            link,
        )
    }

    @Test
    fun `accepts the alternative parameter names`() {
        listOf("sub", "subscription", "link").forEach { key ->
            assertEquals(
                DeepLink.ImportSubscription("https://sub.example/abc"),
                DeepLinks.parse("moonlight://import?$key=https://sub.example/abc"),
            )
        }
    }

    @Test
    fun `accepts the url in the path instead of a query`() {
        assertEquals(
            DeepLink.ImportSubscription("https://sub.example/abc"),
            DeepLinks.parse("moonlight://import/https%3A%2F%2Fsub.example%2Fabc"),
        )
    }

    @Test
    fun `adds a scheme to a bare host`() {
        assertEquals(
            DeepLink.ImportSubscription("https://sub.example/abc"),
            DeepLinks.parse("moonlight://import?url=sub.example/abc"),
        )
    }

    @Test
    fun `rejects non-http targets`() {
        // A deep link must not be able to point the import flow at something that
        // is not a subscription endpoint.
        assertNull(DeepLinks.parse("moonlight://import?url=file:///etc/passwd"))
        assertNull(DeepLinks.parse("moonlight://import?url=javascript:alert(1)"))
        assertNull(DeepLinks.parse("moonlight://import?url="))
        assertNull(DeepLinks.parse("moonlight://import"))
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
