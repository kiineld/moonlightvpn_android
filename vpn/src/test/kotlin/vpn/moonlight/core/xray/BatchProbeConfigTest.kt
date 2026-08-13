package vpn.moonlight.core.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The batch config merges unrelated panel configs into one core instance.
 *
 * The failure to guard against is cross-wiring: panel configs routinely reuse the
 * same tags (`proxy`, `direct`, `block`), so a careless merge would route one
 * node's probe through another node's outbound and report a latency for a server
 * it never touched — a wrong number, not an obvious error.
 */
class BatchProbeConfigTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val options = XrayConfigOptions(socksPort = 10810)

    /** Two configs that both call their outbound `proxy`, as panels do. */
    private fun simple(host: String) = """
        {"outbounds":[
          {"tag":"proxy","protocol":"vless","settings":{"vnext":[{"address":"$host"}]}},
          {"tag":"direct","protocol":"freedom"},
          {"tag":"block","protocol":"blackhole"}
        ]}
    """.trimIndent()

    private fun balanced(host: String) = """
        {"outbounds":[
           {"tag":"proxy-1","protocol":"vless","settings":{"vnext":[{"address":"$host"}]}},
           {"tag":"proxy-2","protocol":"vless","settings":{"vnext":[{"address":"$host"}]}},
           {"tag":"direct","protocol":"freedom"}
         ],
         "routing":{"balancers":[{"tag":"bal","selector":["proxy"]}]},
         "burstObservatory":{"subjectSelector":["proxy"],"pingConfig":{"interval":"5m"}}}
    """.trimIndent()

    private fun parse(config: BatchProbeConfig) = json.parseToJsonElement(config.json) as JsonObject

    private fun JsonObject.array(key: String) = (this[key] as JsonArray).filterIsInstance<JsonObject>()
    private fun JsonObject.text(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.routing() = this["routing"] as JsonObject

    @Test
    fun `gives every node its own inbound on its own port`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(simple("a.example"), 10810), ProbeTarget(simple("b.example"), 10811)),
            options,
        )
        val inbounds = parse(built).array("inbounds")

        assertEquals(2, inbounds.size)
        assertEquals(listOf(10810, 10811), inbounds.map { (it["port"] as JsonPrimitive).intOrNull })
        assertEquals(2, inbounds.mapNotNull { it.text("tag") }.toSet().size)
    }

    @Test
    fun `namespaces colliding tags so two nodes cannot share an outbound`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(simple("a.example"), 10810), ProbeTarget(simple("b.example"), 10811)),
            options,
        )
        val outbounds = parse(built).array("outbounds")
        val tags = outbounds.mapNotNull { it.text("tag") }

        // Six outbounds, six distinct tags — both configs said "proxy".
        assertEquals(6, tags.size)
        assertEquals(6, tags.toSet().size)
    }

    @Test
    fun `pins each inbound to its own node's outbound`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(simple("a.example"), 10810), ProbeTarget(simple("b.example"), 10811)),
            options,
        )
        val root = parse(built)
        val outboundsByTag = root.array("outbounds").associateBy { it.text("tag") }

        root.routing().array("rules").forEach { rule ->
            val inboundTag = (rule["inboundTag"] as JsonArray).single().let {
                (it as JsonPrimitive).content
            }
            val outboundTag = rule.text("outboundTag")!!
            val prefix = inboundTag.substringBefore("_")

            assertTrue("rule for $inboundTag points outside its own node", outboundTag.startsWith("${prefix}_"))
            val address = outboundsByTag.getValue(outboundTag)
                .let { it["settings"] as JsonObject }
                .let { (it["vnext"] as JsonArray).single() as JsonObject }
                .text("address")
            // p0 is the first target, p1 the second.
            assertEquals(if (prefix == "p0") "a.example" else "b.example", address)
        }
    }

    @Test
    fun `prefixes balancer selectors so they still match only their own outbounds`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(balanced("a.example"), 10810), ProbeTarget(balanced("b.example"), 10811)),
            options,
        )
        val root = parse(built)
        val outboundTags = root.array("outbounds").mapNotNull { it.text("tag") }

        root.routing().array("balancers").forEach { balancer ->
            val tag = balancer.text("tag")!!
            val prefix = tag.substringBefore("_")
            val selectors = (balancer["selector"] as JsonArray).map { (it as JsonPrimitive).content }

            selectors.forEach { selector ->
                val matched = outboundTags.filter { it.startsWith(selector) }
                assertTrue("selector $selector matched nothing", matched.isNotEmpty())
                assertTrue(
                    "selector $selector reached another node's outbounds: $matched",
                    matched.all { it.startsWith("${prefix}_") },
                )
            }
        }
    }

    @Test
    fun `routes a balanced node through its balancer, not a single outbound`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(balanced("a.example"), 10810)),
            options,
        )
        val rule = parse(built).routing().array("rules").single()

        assertEquals("p0_bal", rule.text("balancerTag"))
        assertEquals(null, rule.text("outboundTag"))
    }

    @Test
    fun `unions observatory subjects across the batch, each prefixed`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(balanced("a.example"), 10810), ProbeTarget(balanced("b.example"), 10811)),
            options,
        )
        val observatory = parse(built)["burstObservatory"] as JsonObject
        val subjects = (observatory["subjectSelector"] as JsonArray).map { (it as JsonPrimitive).content }

        assertEquals(listOf("p0_proxy", "p1_proxy"), subjects)
        // Settings that are not tags survive untouched.
        assertEquals("5m", ((observatory["pingConfig"] as JsonObject).text("interval")))
    }

    @Test
    fun `skips a config with no usable outbound and reports which were accepted`() {
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(
                ProbeTarget(simple("a.example"), 10810),
                ProbeTarget("""{"outbounds":[]}""", 10811),
                ProbeTarget(simple("c.example"), 10812),
            ),
            options,
        )

        assertEquals(listOf(0, 2), built.accepted)
        assertEquals(2, parse(built).array("inbounds").size)
    }

    @Test
    fun `refuses a batch where nothing is usable`() {
        val thrown = runCatching {
            XrayConfigBuilder.batchProbeConfig(
                listOf(ProbeTarget("not json", 10810), ProbeTarget("""{"outbounds":[]}""", 10811)),
                options,
            )
        }.exceptionOrNull()

        assertTrue(thrown is XrayConfigException)
    }

    @Test
    fun `keeps the batch free of the geo rules a probe does not need`() {
        // Two dozen short-lived instances each re-parsing 22 MB of geoip was the
        // reason probe configs are stripped; the batch must not reintroduce it.
        val built = XrayConfigBuilder.batchProbeConfig(
            listOf(ProbeTarget(simple("a.example"), 10810)),
            options,
        )

        assertEquals("AsIs", parse(built).routing().text("domainStrategy"))
        assertTrue(built.json.contains("geoip").not())
        assertNotEquals(null, parse(built)["log"])
    }
}
