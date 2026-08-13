package vpn.moonlight.data.remote

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import vpn.moonlight.data.model.ServerNode

/**
 * Parses Remnawave's JSON subscription: an array of **complete Xray configs**,
 * one per node, each with its own `remarks`, `dns`, `routing` and `outbounds`.
 *
 * This format is the one that matters. A panel can attach a raw XRAY JSON
 * override to any host — a balancer over a dozen outbounds, custom DNS, routing
 * rules — and none of that survives the base64 share-link format, which
 * flattens each host to a single URI. A host whose override is a VLESS balancer
 * shows up in base64 as a lone `ss://` placeholder that does not work.
 *
 * So each node here keeps its whole config verbatim, and the tunnel replaces
 * only the inbound.
 */
object PanelConfigParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val infrastructureTags = setOf("direct", "block", "dns-out", "dns_out")

    fun parse(body: String): List<ServerNode> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull()
        val configs = when (root) {
            is JsonArray -> root
            // Some deployments wrap the array in an object.
            is JsonObject -> root["configs"] as? JsonArray ?: return emptyList()
            else -> return emptyList()
        }

        return configs
            .mapNotNull { element -> (element as? JsonObject)?.let(::toNode) }
            .distinctBy { it.id }
    }

    private fun toNode(config: JsonObject): ServerNode? {
        val outbounds = config["outbounds"] as? JsonArray ?: return null
        val proxy = primaryOutbound(outbounds) ?: return null

        val remark = (config["remarks"] as? JsonPrimitive)?.contentOrNull
        val parsed = RemarkParser.parse(remark)
        val (host, port) = endpoint(proxy)

        val balancers = (config["routing"] as? JsonObject)?.get("balancers") as? JsonArray
        val isBalancer = balancers != null && balancers.isNotEmpty()

        val name = parsed.name ?: host ?: return null

        return ServerNode(
            // Hashed from the label and endpoint rather than the whole config, so
            // a panel-side tweak to routing does not invalidate a pinned choice.
            id = stableId("${remark.orEmpty()}|$host|$port"),
            name = name,
            panelConfigJson = config.toString(),
            shareLink = null,
            flag = parsed.flag,
            countryCode = parsed.countryCode,
            squad = parsed.squad,
            protocolLabel = protocolLabel(proxy),
            host = host,
            port = port,
            isBalancer = isBalancer,
        )
    }

    /**
     * The outbound the node is actually about. Panel configs always carry
     * `direct` and `block` alongside it, and a balancer config carries many
     * proxies — the one tagged `proxy` is the primary by convention.
     */
    private fun primaryOutbound(outbounds: JsonArray): JsonObject? {
        val objects = outbounds.filterIsInstance<JsonObject>()
        objects.firstOrNull { (it["tag"] as? JsonPrimitive)?.contentOrNull == "proxy" }
            ?.let { return it }
        return objects.firstOrNull { outbound ->
            val tag = (outbound["tag"] as? JsonPrimitive)?.contentOrNull
            val protocol = (outbound["protocol"] as? JsonPrimitive)?.contentOrNull
            tag !in infrastructureTags && protocol !in setOf("freedom", "blackhole", "dns")
        }
    }

    private fun protocolLabel(outbound: JsonObject): String {
        val protocol = (outbound["protocol"] as? JsonPrimitive)?.contentOrNull.orEmpty()
        val security = ((outbound["streamSettings"] as? JsonObject)?.get("security") as? JsonPrimitive)
            ?.contentOrNull
            ?.lowercase()

        val base = when (protocol.lowercase()) {
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "shadowsocks" -> "Shadowsocks"
            "hysteria", "hysteria2" -> "Hysteria2"
            "wireguard" -> "WireGuard"
            "" -> "Proxy"
            else -> protocol.uppercase()
        }
        // isBalancer is deliberately not shown: it is an implementation detail of
        // the panel's config, not something a user picking a server acts on.
        return when (security) {
            "reality" -> "$base Reality"
            "tls" -> "$base TLS"
            else -> base
        }
    }

    /** Digs the endpoint out of whichever settings shape the protocol uses. */
    private fun endpoint(outbound: JsonObject): Pair<String?, Int?> {
        val settings = outbound["settings"] as? JsonObject ?: return null to null

        (settings["vnext"] as? JsonArray)?.firstOrNull()?.let { first ->
            val server = first as? JsonObject ?: return@let
            return server.string("address") to server.int("port")
        }
        (settings["servers"] as? JsonArray)?.firstOrNull()?.let { first ->
            val server = first as? JsonObject ?: return@let
            return server.string("address") to server.int("port")
        }
        return settings.string("address") to settings.int("port")
    }

    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.int(key: String) = (this[key] as? JsonPrimitive)?.intOrNull

    private fun stableId(seed: String): String =
        MessageDigest.getInstance("SHA-256").digest(seed.toByteArray())
            .take(8)
            .joinToString("") { "%02x".format(it) }
}
