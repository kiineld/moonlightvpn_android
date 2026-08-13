package vpn.moonlight.core.xray

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** Knobs for the generated Xray config. */
data class XrayConfigOptions(
    val socksPort: Int,
    /**
     * Directory holding `geoip.dat` and `geosite.dat`.
     *
     * Passed through the config's root `env` object, which is the only mechanism
     * that works: libXray's docs state a top-level `env` on the request is
     * ignored, and setting `XRAY_LOCATION_ASSET` in the process environment from
     * Java does not reach the Go runtime — Xray falls back to its executable
     * directory and looks for `/system/bin/geosite.dat`.
     */
    val assetDir: String? = null,
    val dnsServers: List<String> = listOf("1.1.1.1", "8.8.8.8"),
    /**
     * `warning` records the core's startup banner and every real failure, and
     * nothing else — a few hundred bytes for a healthy session.
     *
     * Not `info`: that logs a line per connection including sniffed domains and
     * destination addresses. Measured on a real session, 591 such lines and 2.6 MB
     * within three minutes. That is a browsing history, in a file the log screen
     * actively invites the user to send to someone else, and it grows without
     * bound. Detail like that belongs behind the verbose toggle, which says so.
     */
    val logLevel: String = "warning",
    /**
     * File the core writes its own log to. Without it the core's output stays
     * inside Go and never reaches the app, so a failed handshake is invisible.
     */
    val logFile: String? = null,
    /** Keep LAN and loopback traffic off the tunnel. */
    val bypassPrivateAddresses: Boolean = true,
    val enableSniffing: Boolean = true,
)

class XrayConfigException(message: String) : Exception(message)

/**
 * Assembles the config handed to `runXrayFromJson`.
 *
 * The outbound is not built here — `convertShareLinksToXrayJson` in the core
 * parses the share link, and this only grafts the result into a wrapper with a
 * SOCKS inbound, DNS and routing. That keeps protocol parsing in one place: the
 * core's, which is authoritative.
 */
object XrayConfigBuilder {

    const val PROXY_TAG = "proxy"
    const val DIRECT_TAG = "direct"
    const val BLOCK_TAG = "block"
    private const val ASSET_LOCATION_KEY = "xray.location.asset"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Address blocks that must never enter the tunnel: loopback, RFC1918, CGNAT,
     * link-local, and multicast. Expressed as literal CIDRs rather than
     * `geoip:private`, so no geoip.dat has to be shipped or loaded.
     */
    private val privateRanges = listOf(
        "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16",
        "172.16.0.0/12", "192.0.0.0/24", "192.168.0.0/16", "224.0.0.0/4",
        "255.255.255.255/32", "::1/128", "fc00::/7", "fe80::/10",
    )

    /**
     * Adapts a config the panel served in its JSON subscription.
     *
     * The panel's config is kept **verbatim** apart from its inbounds — its DNS,
     * routing rules, balancers and burst observatory are all deliberate and
     * usually better tuned than anything generated here. Only the listener is
     * ours, because the port has to be one we picked.
     *
     * The replacement inbound keeps the tag `socks`, which is what Remnawave
     * names it, so any routing rule that references an inbound tag still resolves.
     */
    fun fromPanelConfig(panelConfigJson: String, options: XrayConfigOptions): String {
        val root = runCatching { json.parseToJsonElement(panelConfigJson) }.getOrNull() as? JsonObject
            ?: throw XrayConfigException("panel config is not a JSON object")

        if ((root["outbounds"] as? JsonArray).isNullOrEmpty()) {
            throw XrayConfigException("panel config has no outbounds")
        }

        return JsonObject(
            root.toMutableMap().apply {
                this["inbounds"] = buildJsonArray { add(socksInbound(options)) }
                mergeEnv(this, options)
                // Always replaced, not just filled in when absent: the panel's log
                // block has no file, and without one the core's diagnostics are
                // unreachable from inside the app.
                this["log"] = logBlock(options)
            },
        ).toString()
    }

    private fun logBlock(options: XrayConfigOptions): JsonObject = buildJsonObject {
        put("loglevel", options.logLevel)
        // Deliberately off. The access log is a line per connection including the
        // destination host — a browsing history, in a file the user is encouraged
        // to send to someone else. The error log at info covers diagnosis without it.
        put("access", "none")
        options.logFile?.let { put("error", it) }
    }

    /**
     * Xray reads its runtime options from the config's root `env`. The key is the
     * dotted form; `XRAY_LOCATION_ASSET` is only its environment-variable alias.
     */
    private fun environment(options: XrayConfigOptions): JsonObject? {
        val dir = options.assetDir ?: return null
        return buildJsonObject { put(ASSET_LOCATION_KEY, dir) }
    }

    /** Keeps whatever the panel put in `env` and adds the asset location to it. */
    private fun mergeEnv(target: MutableMap<String, JsonElement>, options: XrayConfigOptions) {
        val dir = options.assetDir ?: return
        val existing = target["env"] as? JsonObject
        target["env"] = JsonObject(
            (existing?.toMutableMap() ?: mutableMapOf()).apply {
                this[ASSET_LOCATION_KEY] = JsonPrimitive(dir)
            },
        )
    }

    private fun socksInbound(options: XrayConfigOptions): JsonObject = buildJsonObject {
        put("tag", "socks")
        put("listen", "127.0.0.1")
        put("port", options.socksPort)
        put("protocol", "socks")
        putJsonObject("settings") {
            put("auth", "noauth")
            // The tunnel carries DNS as UDP, so UDP relay is required.
            put("udp", true)
        }
        if (options.enableSniffing) {
            putJsonObject("sniffing") {
                put("enabled", true)
                putJsonArray("destOverride") { add("http"); add("tls"); add("quic") }
                put("routeOnly", false)
            }
        }
    }

    /**
     * A minimal config for latency probing: enough to dial through the node, and
     * nothing else.
     *
     * DNS and the panel's routing rules are dropped deliberately. Those rules are
     * full of `geosite:`/`geoip:` references, and loading 22 MB of geodata for
     * each of two dozen short-lived probe instances would dominate the
     * measurement. Balancers and the burst observatory are kept, because for a
     * balancer node the balancer *is* what we want to measure.
     */
    fun probeConfig(fullConfigJson: String, options: XrayConfigOptions): String {
        val root = runCatching { json.parseToJsonElement(fullConfigJson) }.getOrNull() as? JsonObject
            ?: throw XrayConfigException("probe config is not a JSON object")
        val outbounds = root["outbounds"] as? JsonArray
            ?: throw XrayConfigException("probe config has no outbounds")
        if (outbounds.isEmpty()) throw XrayConfigException("probe config has no outbounds")

        val balancers = (root["routing"] as? JsonObject)?.get("balancers") as? JsonArray
        val balancerTag = balancers
            ?.filterIsInstance<JsonObject>()
            ?.firstOrNull()
            ?.let { (it["tag"] as? JsonPrimitive)?.contentOrNull }

        return buildJsonObject {
            environment(options)?.let { put("env", it) }
            put("log", logBlock(options))
            putJsonArray("inbounds") { add(socksInbound(options)) }
            put("outbounds", outbounds)
            root["burstObservatory"]?.let { put("burstObservatory", it) }
            putJsonObject("routing") {
                put("domainStrategy", "AsIs")
                if (balancers != null && balancers.isNotEmpty()) put("balancers", balancers)
                putJsonArray("rules") {
                    add(
                        buildJsonObject {
                            put("type", "field")
                            put("network", "tcp,udp")
                            if (balancerTag != null) {
                                put("balancerTag", balancerTag)
                            } else {
                                put("outboundTag", primaryOutboundTag(outbounds))
                            }
                        },
                    )
                }
            }
        }.toString()
    }

    /**
     * The outbound a config is actually about. Panel configs always ship `direct`
     * and `block` beside it, and by Remnawave convention the primary is tagged
     * `proxy`.
     */
    internal fun primaryOutboundTag(outbounds: JsonArray): String {
        val objects = outbounds.filterIsInstance<JsonObject>()
        objects.firstOrNull { (it["tag"] as? JsonPrimitive)?.contentOrNull == PROXY_TAG }
            ?.let { return PROXY_TAG }
        val fallback = objects.firstOrNull { outbound ->
            val tag = (outbound["tag"] as? JsonPrimitive)?.contentOrNull
            val protocol = (outbound["protocol"] as? JsonPrimitive)?.contentOrNull
            tag != DIRECT_TAG && tag != BLOCK_TAG && protocol !in setOf("freedom", "blackhole", "dns")
        }
        return (fallback?.get("tag") as? JsonPrimitive)?.contentOrNull
            ?: throw XrayConfigException("config has no usable outbound")
    }

    /**
     * Builds a config around a share link, for panels that serve only base64.
     *
     * @param convertedConfig the JSON `data` returned by the core's
     *  `convertShareLinksToXrayJson` — a full Xray config whose `outbounds` we take.
     */
    fun build(convertedConfig: String, options: XrayConfigOptions): String {
        val outbound = extractProxyOutbound(convertedConfig)
        return buildJsonObject {
            environment(options)?.let { put("env", it) }
            put("log", logBlock(options))

            putJsonObject("dns") {
                putJsonArray("servers") { options.dnsServers.forEach { add(it) } }
                put("queryStrategy", "UseIP")
            }

            putJsonArray("inbounds") { add(socksInbound(options)) }

            putJsonArray("outbounds") {
                add(outbound)
                add(
                    buildJsonObject {
                        put("tag", DIRECT_TAG)
                        put("protocol", "freedom")
                        putJsonObject("settings") { put("domainStrategy", "UseIP") }
                    },
                )
                add(
                    buildJsonObject {
                        put("tag", BLOCK_TAG)
                        put("protocol", "blackhole")
                    },
                )
            }

            putJsonObject("routing") {
                put("domainStrategy", "IPIfNonMatch")
                putJsonArray("rules") {
                    if (options.bypassPrivateAddresses) {
                        add(
                            buildJsonObject {
                                put("type", "field")
                                put("outboundTag", DIRECT_TAG)
                                putJsonArray("ip") { privateRanges.forEach { add(it) } }
                            },
                        )
                    }
                    add(
                        buildJsonObject {
                            put("type", "field")
                            put("outboundTag", PROXY_TAG)
                            put("network", "tcp,udp")
                        },
                    )
                }
            }
        }.toString()
    }

    /**
     * Takes the first outbound from a converted config and makes it usable.
     *
     * Two fix-ups are mandatory. The core stores the node's display name in
     * `sendThrough` (it has no outbound-name field), so left in place Xray would
     * try to parse a remark like "🇳🇱 Amsterdam" as a bind address and refuse to
     * start. And the tag has to be ours, because the routing rules reference it.
     */
    internal fun extractProxyOutbound(convertedConfig: String): JsonObject {
        val root = runCatching { json.parseToJsonElement(convertedConfig) }.getOrNull() as? JsonObject
            ?: throw XrayConfigException("core returned a config that is not a JSON object")

        val outbounds = root["outbounds"] as? JsonArray
            ?: throw XrayConfigException("core returned a config with no outbounds")

        val first = outbounds.firstOrNull() as? JsonObject
            ?: throw XrayConfigException("core returned an empty outbounds list")

        return JsonObject(
            first
                .filterKeys { it != "sendThrough" }
                .toMutableMap()
                .apply { this["tag"] = JsonPrimitive(PROXY_TAG) },
        )
    }
}
