package vpn.moonlight.core.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a config served by the panel is adapted for the tunnel and for probing. */
class PanelConfigAdaptationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val panelConfig = """
        {
          "remarks": "🇪🇺 Auto",
          "dns": { "servers": ["https://common.dot.dns.yandex.net/dns-query", "1.1.1.1"] },
          "inbounds": [
            { "tag": "socks", "port": 10808, "listen": "127.0.0.1", "protocol": "socks" },
            { "tag": "http", "port": 10809, "listen": "127.0.0.1", "protocol": "http" }
          ],
          "burstObservatory": { "subjectSelector": ["proxy"] },
          "routing": {
            "domainStrategy": "IPIfNonMatch",
            "balancers": [{ "tag": "Balancer_direct", "selector": ["proxy"] }],
            "rules": [
              { "protocol": ["bittorrent"], "outboundTag": "direct" },
              { "domain": ["geosite:category-ru"], "outboundTag": "direct" },
              { "ip": ["geoip:ru"], "outboundTag": "direct" },
              { "network": "tcp,udp", "balancerTag": "Balancer_direct" }
            ]
          },
          "outbounds": [
            { "tag": "proxy", "protocol": "vless" },
            { "tag": "proxy-2", "protocol": "vless" },
            { "tag": "direct", "protocol": "freedom" },
            { "tag": "block", "protocol": "blackhole" }
          ]
        }
    """.trimIndent()

    private val assetDir = "/data/user/0/vpn.moonlight/files/geo"

    private fun tunnel(port: Int = 12345, assets: String? = assetDir) =
        json.parseToJsonElement(
            XrayConfigBuilder.fromPanelConfig(
                panelConfig,
                XrayConfigOptions(socksPort = port, assetDir = assets),
            ),
        ) as JsonObject

    private fun probe(port: Int = 10810) =
        json.parseToJsonElement(
            XrayConfigBuilder.probeConfig(
                panelConfig,
                XrayConfigOptions(socksPort = port, assetDir = assetDir),
            ),
        ) as JsonObject

    private fun JsonObject.array(key: String) = this[key] as JsonArray
    private fun JsonObject.obj(key: String) = this[key] as JsonObject
    private fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull

    @Test
    fun `the tunnel keeps the panel's dns, routing and balancers untouched`() {
        val config = tunnel()

        // These are the panel's deliberate choices and usually better tuned than
        // anything generated here.
        assertEquals(2, config.obj("dns").array("servers").size)
        assertEquals("IPIfNonMatch", config.obj("routing").str("domainStrategy"))
        assertEquals(4, config.obj("routing").array("rules").size)
        assertEquals("Balancer_direct", (config.obj("routing").array("balancers")[0] as JsonObject).str("tag"))
        assertEquals(4, config.array("outbounds").size)
        assertTrue(config.containsKey("burstObservatory"))
    }

    @Test
    fun `the tunnel replaces the inbounds with one socks listener on our port`() {
        val inbounds = tunnel(port = 23456).array("inbounds")
        assertEquals(1, inbounds.size)

        val inbound = inbounds[0] as JsonObject
        assertEquals("socks", inbound.str("protocol"))
        assertEquals(23456, (inbound["port"] as JsonPrimitive).intOrNull)
        assertEquals("127.0.0.1", inbound.str("listen"))
        // Tag kept as "socks", matching the panel, so any inboundTag rule resolves.
        assertEquals("socks", inbound.str("tag"))
        assertTrue((inbound.obj("settings")["udp"] as JsonPrimitive).content.toBoolean())
    }

    @Test
    fun `the geodata location travels in the config root env`() {
        // Regression: this was set with Os.setenv("XRAY_LOCATION_ASSET"), which
        // never reaches the Go runtime. The core then looked in its executable
        // directory and every config failed with stat /system/bin/geosite.dat.
        // libXray's docs are explicit: runtime options belong in the config.
        val env = tunnel().obj("env")
        assertEquals(assetDir, env.str("xray.location.asset"))
    }

    @Test
    fun `a probe config carries the asset location too`() {
        assertEquals(assetDir, probe().obj("env").str("xray.location.asset"))
    }

    @Test
    fun `an env the panel supplied is kept, not overwritten`() {
        val withEnv = panelConfig.replace(
            """"remarks": "🇪🇺 Auto",""",
            """"remarks": "🇪🇺 Auto", "env": { "xray.something.else": "keep-me" },""",
        )
        val config = json.parseToJsonElement(
            XrayConfigBuilder.fromPanelConfig(
                withEnv,
                XrayConfigOptions(socksPort = 1080, assetDir = assetDir),
            ),
        ) as JsonObject

        assertEquals("keep-me", config.obj("env").str("xray.something.else"))
        assertEquals(assetDir, config.obj("env").str("xray.location.asset"))
    }

    @Test
    fun `no env is emitted when there is no asset directory to point at`() {
        assertNull(tunnel(assets = null)["env"])
    }

    @Test
    fun `the core log goes to a file, or it cannot be read from inside the app`() {
        val config = json.parseToJsonElement(
            XrayConfigBuilder.fromPanelConfig(
                panelConfig,
                XrayConfigOptions(socksPort = 1080, logFile = "/data/logs/xray.log"),
            ),
        ) as JsonObject

        assertEquals("/data/logs/xray.log", config.obj("log").str("error"))
    }

    @Test
    fun `the default level is warning, which is not silent and not a history`() {
        // The core logs its startup banner and every failure at warning. info adds
        // a line per connection with the destination host — measured at 591 lines
        // and 2.6 MB in three minutes, in a file meant to be shared.
        assertEquals("warning", tunnel().obj("log").str("loglevel"))
    }

    @Test
    fun `the access log stays off, because it is a browsing history`() {
        // One line per connection including the destination host — not something
        // to put in a file the user is encouraged to send to someone else.
        assertEquals("none", tunnel().obj("log").str("access"))
        assertEquals("none", probe().obj("log").str("access"))
    }

    @Test
    fun `the panel's own log block is replaced, not merged`() {
        // The panel's block has no file, so keeping it would lose the core's output.
        val withLog = panelConfig.replace(
            """"remarks": "🇪🇺 Auto",""",
            """"remarks": "🇪🇺 Auto", "log": { "loglevel": "none" },""",
        )
        val config = json.parseToJsonElement(
            XrayConfigBuilder.fromPanelConfig(
                withLog,
                XrayConfigOptions(socksPort = 1080, logFile = "/data/logs/xray.log"),
            ),
        ) as JsonObject

        assertEquals("warning", config.obj("log").str("loglevel"))
        assertEquals("/data/logs/xray.log", config.obj("log").str("error"))
    }

    @Test
    fun `the probe config drops dns and geo rules so no geodata is loaded`() {
        val config = probe()

        // Loading 22 MB of geoip for each of two dozen short-lived probe
        // instances would dominate the measurement it is supposed to take.
        assertFalse(config.containsKey("dns"))
        val text = config.toString()
        assertFalse(text.contains("geosite:"))
        assertFalse(text.contains("geoip:"))
        assertEquals("AsIs", config.obj("routing").str("domainStrategy"))
    }

    @Test
    fun `the probe config keeps the balancer, because that is what it measures`() {
        val config = probe()
        assertEquals("Balancer_direct", (config.obj("routing").array("balancers")[0] as JsonObject).str("tag"))
        assertTrue(config.containsKey("burstObservatory"))

        val rules = config.obj("routing").array("rules")
        assertEquals(1, rules.size)
        assertEquals("Balancer_direct", (rules[0] as JsonObject).str("balancerTag"))
        assertNull((rules[0] as JsonObject).str("outboundTag"))
    }

    @Test
    fun `a probe config without a balancer routes straight to the proxy outbound`() {
        val single = """
            {"outbounds":[{"tag":"proxy","protocol":"vless"},{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()
        val config = json.parseToJsonElement(
            XrayConfigBuilder.probeConfig(single, XrayConfigOptions(socksPort = 1080)),
        ) as JsonObject

        val rule = config.obj("routing").array("rules")[0] as JsonObject
        assertEquals("proxy", rule.str("outboundTag"))
    }

    @Test
    fun `the primary outbound is never direct or block`() {
        val outbounds = json.parseToJsonElement(
            """[{"tag":"direct","protocol":"freedom"},{"tag":"mynode","protocol":"trojan"},
                {"tag":"block","protocol":"blackhole"}]""",
        ) as JsonArray
        assertEquals("mynode", XrayConfigBuilder.primaryOutboundTag(outbounds))
    }

    @Test
    fun `configs the core could not use are reported rather than silently accepted`() {
        listOf("", "not json", "{}", """{"outbounds":[]}""").forEach { bad ->
            runCatching { XrayConfigBuilder.fromPanelConfig(bad, XrayConfigOptions(socksPort = 1)) }
                .onSuccess { org.junit.Assert.fail("expected failure for: $bad") }
                .onFailure { assertTrue(it is XrayConfigException) }
            runCatching { XrayConfigBuilder.probeConfig(bad, XrayConfigOptions(socksPort = 1)) }
                .onSuccess { org.junit.Assert.fail("expected failure for: $bad") }
                .onFailure { assertTrue(it is XrayConfigException) }
        }
    }
}
