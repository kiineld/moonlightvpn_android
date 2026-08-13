package vpn.moonlight.core.tunnel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import vpn.moonlight.core.MoonlightVpnService

class HevTunnelConfigTest {

    @Test
    fun `carries the socks port the core is listening on`() {
        val yaml = HevTunnelConfig.yaml(socksPort = 12345, mtu = 8500)
        assertTrue(yaml.contains("port: 12345"))
        assertTrue(yaml.contains("address: '127.0.0.1'"))
    }

    @Test
    fun `enables udp relay`() {
        // Without this, DNS over the tunnel silently fails.
        assertTrue(HevTunnelConfig.yaml(1080, 8500).contains("udp: 'udp'"))
    }

    @Test
    fun `mtu matches the value the vpn service sets on the interface`() {
        // A mismatch here drops large packets with no error anywhere.
        val yaml = HevTunnelConfig.yaml(1080, MoonlightVpnService.MTU)
        assertTrue(yaml.contains("mtu: ${MoonlightVpnService.MTU}"))
    }

    @Test
    fun `is a well-formed flat yaml document`() {
        val yaml = HevTunnelConfig.yaml(1080, 8500)
        val sections = yaml.lines().filter { it.isNotBlank() && !it.startsWith(" ") }
        assertEquals(listOf("tunnel:", "socks5:", "misc:"), sections)
        assertTrue("must end with a newline", yaml.endsWith("\n"))
        assertTrue("tabs break yaml parsers", !yaml.contains('\t'))
    }
}
