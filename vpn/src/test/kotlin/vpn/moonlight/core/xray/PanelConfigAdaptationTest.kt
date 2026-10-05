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

    private fun ping(config: String = panelConfig) = XrayConfigBuilder.pingConfig(config)

    private fun PingConfig.root() =
        this@PanelConfigAdaptationTest.json.parseToJsonElement(this.json) as JsonObject

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
    fun `a ping config is the outbounds and nothing else, so no geodata is loaded`() {
        val config = ping().root()

        // The core's probe dials through an outbound directly. Routing rules
        // would mean loading 22 MB of geodata to time one request.
        assertEquals(setOf("outbounds"), config.keys)
        assertEquals(4, config.array("outbounds").size)
        val text = config.toString()
        assertFalse(text.contains("geosite:"))
        assertFalse(text.contains("geoip:"))
    }

    @Test
    fun `a balancer node is timed through every outbound its balancer chooses between`() {
        // The selector is a tag prefix: "proxy" covers proxy and proxy-2, and
        // never direct or block.
        assertEquals(listOf("proxy", "proxy-2"), ping().tags)
    }

    @Test
    fun `a balancer's members are whatever its selector matches, as in the core`() {
        // Found on the emulator: a member was dropped for being a `freedom`
        // outbound, so a balancer whose only live member it was read n/a.
        // Xray matches the selector against tags and asks nothing else.
        val config = """
            {"routing":{"balancers":[{"tag":"b","selector":["proxy"]}]},
             "outbounds":[{"tag":"proxy-1","protocol":"vless"},{"tag":"proxy-2","protocol":"freedom"},
                          {"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()

        assertEquals(listOf("proxy-1", "proxy-2"), ping(config).tags)
    }

    @Test
    fun `a balancer with a dozen members is timed through as many as the core takes at once`() {
        val members = (1..12).joinToString(",") { """{"tag":"proxy-$it","protocol":"vless"}""" }
        val config = """
            {"routing":{"balancers":[{"tag":"b","selector":["proxy"]}]},
             "outbounds":[$members,{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()

        assertEquals((1..MAX_PING_BATCH).map { "proxy-$it" }, ping(config).tags)
    }

    @Test
    fun `a node without a balancer is timed through its proxy outbound`() {
        val single = """
            {"outbounds":[{"tag":"direct","protocol":"freedom"},{"tag":"proxy","protocol":"vless"}]}
        """.trimIndent()

        assertEquals(listOf("proxy"), ping(single).tags)
    }

    @Test
    fun `a balancer that selects nothing falls back to the primary outbound`() {
        val config = """
            {"routing":{"balancers":[{"tag":"b","selector":["nothing-"]}]},
             "outbounds":[{"tag":"mynode","protocol":"trojan"},{"tag":"direct","protocol":"freedom"}]}
        """.trimIndent()

        assertEquals(listOf("mynode"), ping(config).tags)
    }

    @Test
    fun `a share link is timed through its one outbound, without the remark the core leaves in it`() {
        // The core stores the node's display name in sendThrough; left in place
        // it is parsed as a bind address and the outbound is refused.
        val converted = """
            {"outbounds":[{"tag":"x","protocol":"vless","sendThrough":"🇳🇱 Amsterdam"}]}
        """.trimIndent()
        val config = XrayConfigBuilder.pingConfigFromShareLink(converted)

        assertEquals(listOf("proxy"), config.tags)
        val outbound = config.root().array("outbounds")[0] as JsonObject
        assertEquals("proxy", outbound.str("tag"))
        assertNull(outbound["sendThrough"])
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
            runCatching { XrayConfigBuilder.pingConfig(bad) }
                .onSuccess { org.junit.Assert.fail("expected failure for: $bad") }
                .onFailure { assertTrue(it is XrayConfigException) }
        }
    }
}
