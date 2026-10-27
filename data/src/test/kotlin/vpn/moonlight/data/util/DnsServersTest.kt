package vpn.moonlight.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DnsServersTest {

    @Test
    fun `accepts the forms xray takes`() {
        assertEquals(listOf("1.1.1.1"), DnsServers.parse(" 1.1.1.1 "))
        assertEquals(
            listOf("https://dns.adguard-dns.com/dns-query", "tcp://8.8.8.8:53", "2606:4700:4700::1111"),
            DnsServers.parse("https://dns.adguard-dns.com/dns-query, tcp://8.8.8.8:53\n2606:4700:4700::1111"),
        )
        assertEquals(listOf("quic+local://dns.adguard.com"), DnsServers.parse("quic+local://dns.adguard.com"))
    }

    @Test
    fun `refuses anything the core would choke on`() {
        listOf("", "   ", "dns.google", "1.1.1", "256.1.1.1", "ftp://x.y", "https://", "https://bad host/", "tcp://1.1.1.1:99999", "\"1.1.1.1\"")
            .forEach { assertNull("accepted: $it", DnsServers.parse(it)) }
    }
}
