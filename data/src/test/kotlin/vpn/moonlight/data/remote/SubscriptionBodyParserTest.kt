package vpn.moonlight.data.remote

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionBodyParserTest {

    private val linkA = "vless://a@a.example:443?security=reality#A"
    private val linkB = "trojan://b@b.example:443#B"

    @Test
    fun `decodes a base64 subscription`() {
        val body = Base64.getEncoder().encodeToString("$linkA\n$linkB".toByteArray())
        val parsed = SubscriptionBodyParser.parse(body)
        assertEquals(listOf(linkA, linkB), parsed.links)
    }

    @Test
    fun `decodes url-safe unpadded base64`() {
        val raw = "$linkA\n$linkB"
        val body = Base64.getUrlEncoder().withoutPadding().encodeToString(raw.toByteArray())
        assertEquals(listOf(linkA, linkB), SubscriptionBodyParser.parse(body).links)
    }

    @Test
    fun `accepts plain text that was never encoded`() {
        val parsed = SubscriptionBodyParser.parse("$linkA\n\n$linkB\n")
        assertEquals(listOf(linkA, linkB), parsed.links)
    }

    @Test
    fun `reads a remnawave-shaped json body`() {
        val body = """
            {
              "subscriptionUrl": "https://sub.example/abc",
              "user": {
                "username": "kii",
                "usedTrafficBytes": 26630000000,
                "trafficLimitBytes": 107374182400,
                "expiresAt": "2026-08-25T00:00:00Z",
                "hwidDeviceLimit": 5
              },
              "links": ["$linkA", "$linkB"]
            }
        """.trimIndent()

        val parsed = SubscriptionBodyParser.parse(body)
        assertEquals(listOf(linkA, linkB), parsed.links)
        val info = parsed.userInfo!!
        assertEquals(26_630_000_000L, info.usedBytes)
        assertEquals(107_374_182_400L, info.totalBytes)
        assertEquals(5, info.deviceLimit)
        // 2026-08-25T00:00:00Z
        assertEquals(1_787_616_000L, info.expiresAtEpochSeconds)
    }

    @Test
    fun `reads a bare json array of links`() {
        val parsed = SubscriptionBodyParser.parse("""["$linkA","$linkB"]""")
        assertEquals(listOf(linkA, linkB), parsed.links)
    }

    @Test
    fun `unwraps a base64 payload nested in a json field`() {
        val inner = Base64.getEncoder().encodeToString("$linkA\n$linkB".toByteArray())
        val parsed = SubscriptionBodyParser.parse("""{"data":"$inner"}""")
        assertEquals(listOf(linkA, linkB), parsed.links)
    }

    @Test
    fun `decodes a title and announcement when present`() {
        val parsed = SubscriptionBodyParser.parse(
            """{"profileTitle":"Luna","announce":"Maintenance Friday","links":["$linkA"]}""",
        )
        assertEquals("Luna", parsed.title)
        assertEquals("Maintenance Friday", parsed.announce)
    }

    @Test
    fun `empty and unusable bodies yield no links instead of throwing`() {
        assertTrue(SubscriptionBodyParser.parse("").links.isEmpty())
        assertTrue(SubscriptionBodyParser.parse("   ").links.isEmpty())
        assertTrue(SubscriptionBodyParser.parse("<html>nope</html>").links.isEmpty())
        assertTrue(SubscriptionBodyParser.parse("{}").links.isEmpty())
    }

    @Test
    fun `a json body with no recognisable usage reports no user info`() {
        assertNull(SubscriptionBodyParser.parse("""{"links":["$linkA"]}""").userInfo)
    }
}
