package vpn.moonlight.core.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayConfigBuilderTest {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * What the core actually returns from convertShareLinksToXrayJson: a full
     * Xray config whose outbound carries the node's display name in
     * `sendThrough`, because Xray has no outbound-name field.
     */
    private val coreOutput = """
        {
          "outbounds": [
            {
              "protocol": "vless",
              "sendThrough": "🇳🇱 Amsterdam",
              "settings": { "vnext": [ { "address": "nl1.example.net", "port": 443 } ] },
              "streamSettings": { "network": "tcp", "security": "reality" }
            }
          ]
        }
    """.trimIndent()

    private fun build(core: String = coreOutput, port: Int = 10808) =
        json.parseToJsonElement(
            XrayConfigBuilder.build(core, XrayConfigOptions(socksPort = port)),
        ) as JsonObject

    private fun JsonObject.array(key: String) = this[key] as JsonArray
    private fun JsonObject.obj(key: String) = this[key] as JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    @Test
    fun `strips sendThrough, which is a display name and not a bind address`() {
        // Left in place, Xray would try to parse "🇳🇱 Amsterdam" as an address
        // and refuse to start. This is the single most load-bearing fix-up here.
        val outbound = build().array("outbounds")[0] as JsonObject
        assertFalse("sendThrough must not survive", outbound.containsKey("sendThrough"))
        assertEquals("vless", outbound.str("protocol"))
    }

    @Test
    fun `retags the outbound so routing rules can reference it`() {
        val outbound = build().array("outbounds")[0] as JsonObject
        assertEquals(XrayConfigBuilder.PROXY_TAG, outbound.str("tag"))
    }

    @Test
    fun `preserves the parsed protocol payload untouched`() {
        val outbound = build().array("outbounds")[0] as JsonObject
        val vnext = (outbound.obj("settings")["vnext"] as JsonArray)[0] as JsonObject
        assertEquals("nl1.example.net", vnext.str("address"))
        assertEquals(443, (vnext["port"] as JsonPrimitive).intOrNull)
        assertEquals("reality", outbound.obj("streamSettings").str("security"))
    }

    @Test
    fun `emits a loopback socks inbound with udp enabled`() {
        val inbound = build(port = 12345).array("inbounds")[0] as JsonObject
        assertEquals("socks", inbound.str("protocol"))
        assertEquals("127.0.0.1", inbound.str("listen"))
        assertEquals(12345, (inbound["port"] as JsonPrimitive).intOrNull)
        // Without UDP relay, DNS carried through the tunnel would fail.
        assertTrue((inbound.obj("settings")["udp"] as JsonPrimitive).booleanOrNull == true)
    }

    @Test
    fun `always provides direct and block outbounds alongside the proxy`() {
        val tags = build().array("outbounds").map { (it as JsonObject).str("tag") }
        assertEquals(
            listOf(XrayConfigBuilder.PROXY_TAG, XrayConfigBuilder.DIRECT_TAG, XrayConfigBuilder.BLOCK_TAG),
            tags,
        )
    }

    @Test
    fun `routes private address space direct without needing geoip data`() {
        val rules = build().obj("routing").array("rules")
        val bypass = rules
            .map { it as JsonObject }
            .first { it.str("outboundTag") == XrayConfigBuilder.DIRECT_TAG }
        val ranges = (bypass["ip"] as JsonArray).mapNotNull { (it as JsonPrimitive).contentOrNull }

        listOf("10.0.0.0/8", "192.168.0.0/16", "127.0.0.0/8", "::1/128", "fc00::/7").forEach {
            assertTrue("expected $it to bypass the tunnel", ranges.contains(it))
        }
        // Literal CIDRs, not geoip:private — so no geoip.dat has to ship.
        assertTrue(ranges.none { it.startsWith("geoip:") })
    }

    @Test
    fun `the catch-all rule sends everything else through the proxy`() {
        val rules = build().obj("routing").array("rules").map { it as JsonObject }
        val last = rules.last()
        assertEquals(XrayConfigBuilder.PROXY_TAG, last.str("outboundTag"))
        assertEquals("tcp,udp", last.str("network"))
    }

    @Test
    fun `bypass can be turned off`() {
        val config = json.parseToJsonElement(
            XrayConfigBuilder.build(
                coreOutput,
                XrayConfigOptions(socksPort = 1080, bypassPrivateAddresses = false),
            ),
        ) as JsonObject
        val rules = config.obj("routing").array("rules")
        assertEquals(1, rules.size)
    }

    @Test
    fun `configures dns servers for the core`() {
        val config = json.parseToJsonElement(
            XrayConfigBuilder.build(
                coreOutput,
                XrayConfigOptions(socksPort = 1080, dnsServers = listOf("9.9.9.9")),
            ),
        ) as JsonObject
        val servers = config.obj("dns").array("servers").mapNotNull { (it as JsonPrimitive).contentOrNull }
        assertEquals(listOf("9.9.9.9"), servers)
    }

    @Test
    fun `a config the core could not parse is reported, not silently accepted`() {
        listOf("", "not json", "[]", "{}", """{"outbounds":[]}""").forEach { bad ->
            runCatching { XrayConfigBuilder.build(bad, XrayConfigOptions(socksPort = 1080)) }
                .onSuccess { org.junit.Assert.fail("expected a failure for: $bad") }
                .onFailure { assertTrue(it is XrayConfigException) }
        }
    }

    @Test
    fun `takes the first outbound when the core returns several`() {
        val multi = """
            {"outbounds":[
              {"protocol":"vless","tag":"first"},
              {"protocol":"trojan","tag":"second"}
            ]}
        """.trimIndent()
        val outbound = XrayConfigBuilder.extractProxyOutbound(multi)
        assertEquals("vless", (outbound["protocol"] as JsonPrimitive).contentOrNull)
        assertNotNull(outbound["tag"])
        assertEquals(XrayConfigBuilder.PROXY_TAG, (outbound["tag"] as JsonPrimitive).contentOrNull)
    }
}
