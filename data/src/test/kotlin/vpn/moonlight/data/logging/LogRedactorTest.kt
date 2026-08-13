package vpn.moonlight.data.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun `masks the uuid in a share link`() {
        val out = LogRedactor.redact(
            "dialing vless://8f31c2a7-0000-4000-8000-000000000000@nl1.example.net:443",
        )
        assertFalse(out.contains("8f31c2a7"))
        assertTrue(out.contains("nl1.example.net:443"))
    }

    @Test
    fun `masks a subscription path but keeps the host`() {
        // The path is the credential: it hands over the whole node list.
        val out = LogRedactor.redact("fetching https://sub.example.site/aBcD1234EfGh5678")
        assertFalse(out.contains("aBcD1234EfGh5678"))
        assertTrue(out.contains("sub.example.site"))
    }

    @Test
    fun `masks reality keys and passwords in query strings`() {
        val out = LogRedactor.redact("?security=reality&pbk=abcdefgh12345678&sni=example.com")
        assertFalse(out.contains("abcdefgh12345678"))
        assertTrue(out.contains("security=reality"))
    }

    @Test
    fun `masks long encoded blobs`() {
        val blob = "Y2hhY2hhMjAtaWV0Zi1wb2x5MTMwNTpORTYycEJXTkw0UDNvN0JB"
        assertFalse(LogRedactor.redact("payload $blob").contains(blob))
    }

    @Test
    fun `leaves ordinary diagnostics readable`() {
        val message = "core failed to start: geosite.dat not found in /data/user/0/vpn.moonlight/files/geo"
        assertEquals(message, LogRedactor.redact(message))
    }

    @Test
    fun `a plain host with no path survives`() {
        assertEquals("GET https://example.com", LogRedactor.redact("GET https://example.com"))
    }
}
