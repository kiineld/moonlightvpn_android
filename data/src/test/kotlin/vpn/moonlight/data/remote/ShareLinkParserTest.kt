package vpn.moonlight.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {

    private val realityLink =
        "vless://8f31c2a7-0000-4000-8000-000000000000@nl1.example.net:443" +
            "?type=tcp&security=reality&pbk=abc&fp=chrome&sni=example.com#%F0%9F%87%B3%F0%9F%87%B1%20Amsterdam"

    @Test
    fun `reads flag, name, host, port and protocol from a reality link`() {
        val node = ShareLinkParser.parse(realityLink)!!

        assertEquals("🇳🇱", node.flag)
        assertEquals("NL", node.countryCode)
        assertEquals("Amsterdam", node.name)
        assertEquals("nl1.example.net", node.host)
        assertEquals(443, node.port)
        assertEquals("VLESS Reality", node.protocolLabel)
    }

    @Test
    fun `splits a squad off the remark`() {
        val node = ShareLinkParser.parse(
            "vless://id@h.example:443?security=reality#%F0%9F%87%AB%F0%9F%87%AE%20Helsinki%20%7C%20Europe",
        )!!
        assertEquals("Helsinki", node.name)
        assertEquals("Europe", node.squad)
    }

    @Test
    fun `falls back to the host when the remark is missing`() {
        val node = ShareLinkParser.parse("vless://id@fallback.example:8443?security=tls")!!
        assertEquals("fallback.example", node.name)
        assertEquals("VLESS TLS", node.protocolLabel)
        assertNull(node.flag)
        assertNull(node.countryCode)
    }

    @Test
    fun `ids are stable across parses and distinct across links`() {
        val first = ShareLinkParser.parse(realityLink)!!
        val second = ShareLinkParser.parse(realityLink)!!
        val other = ShareLinkParser.parse("vless://id@other.example:443#Other")!!

        assertEquals(first.id, second.id)
        assertNotEquals(first.id, other.id)
    }

    @Test
    fun `recognises the protocols a panel can hand us`() {
        assertTrue(ShareLinkParser.isShareLink("vless://x"))
        assertTrue(ShareLinkParser.isShareLink("vmess://x"))
        assertTrue(ShareLinkParser.isShareLink("trojan://x"))
        assertTrue(ShareLinkParser.isShareLink("ss://x"))
        assertTrue(ShareLinkParser.isShareLink("  VLESS://x  "))
        assertFalse(ShareLinkParser.isShareLink("https://sub.example/abc"))
        assertFalse(ShareLinkParser.isShareLink("not a link"))
    }

    @Test
    fun `parseAll drops junk and de-duplicates`() {
        val nodes = ShareLinkParser.parseAll(
            listOf(realityLink, realityLink, "https://example.com", "", "vless://id@b.example:443#B"),
        )
        assertEquals(2, nodes.size)
    }

    @Test
    fun `a vmess blob still parses without an authority`() {
        // vmess carries base64 rather than user@host, so host stays unknown —
        // it must not throw or be dropped.
        val node = ShareLinkParser.parse("vmess://eyJhZGQiOiJ4In0=")
        assertEquals("VMess", node!!.protocolLabel)
    }
}
