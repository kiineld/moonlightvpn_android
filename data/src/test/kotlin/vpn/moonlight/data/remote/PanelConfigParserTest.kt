package vpn.moonlight.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelConfigParserTest {

    /**
     * Shaped after what a Remnawave panel actually serves at `<url>/json`: an
     * array of complete configs. The first is a balancer whose XRAY JSON override
     * spans several VLESS outbounds — the case the base64 format destroys.
     */
    private val body = """
        [
          {
            "remarks": "🇪🇺 Auto",
            "dns": { "servers": ["1.1.1.1"] },
            "inbounds": [{ "tag": "socks", "port": 10808, "protocol": "socks" }],
            "routing": {
              "balancers": [{ "tag": "Balancer_direct", "selector": ["proxy"] }],
              "rules": [{ "domain": ["geosite:youtube"], "balancerTag": "Balancer_direct" }]
            },
            "outbounds": [
              { "tag": "proxy", "protocol": "vless",
                "settings": { "vnext": [{ "address": "a.example", "port": 443 }] },
                "streamSettings": { "security": "reality" } },
              { "tag": "proxy-2", "protocol": "vless",
                "settings": { "vnext": [{ "address": "b.example", "port": 443 }] },
                "streamSettings": { "security": "reality" } },
              { "tag": "direct", "protocol": "freedom" },
              { "tag": "block", "protocol": "blackhole" }
            ]
          },
          {
            "remarks": "🇷🇺 Russia | Игровой",
            "inbounds": [],
            "routing": { "rules": [] },
            "outbounds": [
              { "tag": "proxy", "protocol": "hysteria",
                "settings": { "address": "moscow.example", "port": 8443 } },
              { "tag": "direct", "protocol": "freedom" },
              { "tag": "block", "protocol": "blackhole" }
            ]
          },
          {
            "remarks": "🇱🇻 Latvia",
            "outbounds": [
              { "tag": "proxy", "protocol": "vless",
                "settings": { "vnext": [{ "address": "lv.example", "port": 1443 }] },
                "streamSettings": { "security": "tls" } },
              { "tag": "direct", "protocol": "freedom" }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `reads every config in the array`() {
        assertEquals(3, PanelConfigParser.parse(body).size)
    }

    @Test
    fun `keeps each config verbatim so panel overrides survive`() {
        val auto = PanelConfigParser.parse(body).first()
        val config = auto.panelConfigJson!!

        // The whole override has to be retained: balancer, its rules, and DNS.
        assertTrue(config.contains("Balancer_direct"))
        assertTrue(config.contains("geosite:youtube"))
        assertTrue(config.contains("proxy-2"))
        assertTrue(config.contains("1.1.1.1"))
        assertNull("a panel node has no share link", auto.shareLink)
        assertTrue(auto.hasPanelConfig)
    }

    @Test
    fun `flags a balancer config as such`() {
        val nodes = PanelConfigParser.parse(body)
        assertTrue(nodes[0].isBalancer)
        assertFalse(nodes[1].isBalancer)
        // The flag is kept in the model but deliberately absent from the label —
        // it is a panel implementation detail, not a user-facing property.
        assertEquals("VLESS Reality", nodes[0].protocolLabel)
    }

    @Test
    fun `the panel's auto node is recognised by the auto selection`() {
        assertTrue(PanelConfigParser.parse(body).first().isAutoNode)
    }

    @Test
    fun `reads remark, flag and country`() {
        val nodes = PanelConfigParser.parse(body)
        assertEquals("Auto", nodes[0].name)
        assertEquals("🇪🇺", nodes[0].flag)
        assertEquals("Russia", nodes[1].name)
        assertEquals("Игровой", nodes[1].squad)
        assertEquals("RU", nodes[1].countryCode)
    }

    @Test
    fun `labels protocols from the primary outbound, ignoring direct and block`() {
        val nodes = PanelConfigParser.parse(body)
        assertEquals("Hysteria2", nodes[1].protocolLabel)
        assertEquals("VLESS TLS", nodes[2].protocolLabel)
    }

    @Test
    fun `finds the endpoint whichever settings shape the protocol uses`() {
        val nodes = PanelConfigParser.parse(body)
        // vnext for vless
        assertEquals("a.example", nodes[0].host)
        assertEquals(443, nodes[0].port)
        // flat address/port for hysteria
        assertEquals("moscow.example", nodes[1].host)
        assertEquals(8443, nodes[1].port)
    }

    @Test
    fun `ids are stable across fetches and distinct between nodes`() {
        val first = PanelConfigParser.parse(body)
        val second = PanelConfigParser.parse(body)
        assertEquals(first.map { it.id }, second.map { it.id })
        assertEquals(3, first.map { it.id }.toSet().size)
        assertNotEquals(first[0].id, first[1].id)
    }

    @Test
    fun `a panel-side routing tweak does not invalidate a pinned node`() {
        // The id is hashed from label and endpoint, not the whole config, so
        // editing routing rules must not change it.
        val edited = body.replace("geosite:youtube", "geosite:tiktok")
        assertEquals(PanelConfigParser.parse(body)[0].id, PanelConfigParser.parse(edited)[0].id)
    }

    @Test
    fun `bodies that are not a config array yield nothing`() {
        listOf("", "  ", "not json", "{}", "[]", """{"links":["vless://x"]}""").forEach {
            assertTrue("failed for: $it", PanelConfigParser.parse(it).isEmpty())
        }
    }

    @Test
    fun `a config with no outbounds is skipped rather than crashing`() {
        val partial = """[{"remarks":"broken","outbounds":[]},{"remarks":"🇱🇻 ok",
            "outbounds":[{"tag":"proxy","protocol":"vless",
            "settings":{"vnext":[{"address":"x.example","port":443}]}}]}]"""
        val nodes = PanelConfigParser.parse(partial)
        assertEquals(1, nodes.size)
        assertEquals("ok", nodes[0].name)
    }
}
