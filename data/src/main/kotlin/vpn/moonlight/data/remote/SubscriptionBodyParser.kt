package vpn.moonlight.data.remote

import java.time.Instant
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import vpn.moonlight.data.model.SubscriptionUserInfo

/** What a subscription body yielded, before it is merged with response headers. */
data class ParsedSubscriptionBody(
    val links: List<String>,
    val title: String? = null,
    val userInfo: SubscriptionUserInfo? = null,
    val announce: String? = null,
)

/**
 * Decodes a subscription body.
 *
 * Panels disagree on format, so all four shapes in the wild are accepted: a
 * Remnawave-style JSON object, a bare JSON array of links, base64-encoded
 * newline-separated links, and plain text. Detection is by content, not by the
 * `Content-Type` header, because panels frequently mislabel it.
 */
object SubscriptionBodyParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val titleKeys = listOf("profileTitle", "title", "name", "profile_title")
    private val announceKeys = listOf("announce", "announcement", "message")
    private val usedBytesKeys = listOf("usedTrafficBytes", "usedTraffic", "used_traffic_bytes", "upload_download")
    private val totalBytesKeys = listOf("trafficLimitBytes", "trafficLimit", "traffic_limit_bytes", "total")
    private val expiryKeys = listOf("expiresAt", "expireAt", "expiry", "expire", "expires_at")
    private val deviceLimitKeys = listOf("hwidDeviceLimit", "deviceLimit", "device_limit")
    private val devicesUsedKeys = listOf("hwidDeviceCount", "devicesUsed", "devices_used", "activeDevices")

    fun parse(body: String): ParsedSubscriptionBody {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ParsedSubscriptionBody(emptyList())

        parseAsJson(trimmed)?.let { return it }
        decodeBase64(trimmed)?.let { decoded ->
            val links = splitLinks(decoded)
            if (links.isNotEmpty()) return ParsedSubscriptionBody(links)
        }
        return ParsedSubscriptionBody(splitLinks(trimmed))
    }

    private fun parseAsJson(body: String): ParsedSubscriptionBody? {
        if (body.firstOrNull() != '{' && body.firstOrNull() != '[') return null
        val element = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return null

        if (element is JsonArray) {
            val links = element.mapNotNull { it.asStringOrNull() }.filter(ShareLinkParser::isShareLink)
            return links.takeIf { it.isNotEmpty() }?.let { ParsedSubscriptionBody(it) }
        }
        if (element !is JsonObject) return null

        val links = findLinks(element)
        if (links.isEmpty()) return null

        val user = element["user"] as? JsonObject
        val scopes = listOfNotNull(user, element)

        return ParsedSubscriptionBody(
            links = links,
            title = ProfileTitle.decode(
                scopes.firstNotNullOfOrNull { scope -> titleKeys.firstNotNullOfOrNull { scope.string(it) } },
            ),
            announce = scopes.firstNotNullOfOrNull { scope -> announceKeys.firstNotNullOfOrNull { scope.string(it) } },
            userInfo = userInfo(scopes),
        )
    }

    private fun userInfo(scopes: List<JsonObject>): SubscriptionUserInfo? {
        val used = scopes.firstNotNullOfOrNull { s -> usedBytesKeys.firstNotNullOfOrNull { s.long(it) } }
        val total = scopes.firstNotNullOfOrNull { s -> totalBytesKeys.firstNotNullOfOrNull { s.long(it) } }
        val expiry = scopes.firstNotNullOfOrNull { s -> expiryKeys.firstNotNullOfOrNull { s.epochSeconds(it) } }
        val deviceLimit = scopes.firstNotNullOfOrNull { s -> deviceLimitKeys.firstNotNullOfOrNull { s.long(it) } }
        val devicesUsed = scopes.firstNotNullOfOrNull { s -> devicesUsedKeys.firstNotNullOfOrNull { s.long(it) } }

        if (used == null && total == null && expiry == null && deviceLimit == null) return null
        return SubscriptionUserInfo(
            // The JSON form reports a single combined figure; the header form
            // splits it. Put it on download so the sum stays correct.
            downloadBytes = used,
            totalBytes = total,
            expiresAtEpochSeconds = expiry,
            deviceLimit = deviceLimit?.toInt(),
            devicesUsed = devicesUsed?.toInt(),
        )
    }

    /** Finds the link array wherever the panel put it. */
    private fun findLinks(root: JsonObject): List<String> {
        for (key in listOf("links", "subscriptionLinks", "configs", "urls")) {
            val array = root[key] as? JsonArray ?: continue
            val links = array.mapNotNull { it.asStringOrNull() }.filter(ShareLinkParser::isShareLink)
            if (links.isNotEmpty()) return links
        }
        // Some panels wrap the raw subscription payload in a JSON string field.
        for (key in listOf("subscription", "data", "raw")) {
            val raw = root.string(key) ?: continue
            val decoded = decodeBase64(raw) ?: raw
            val links = splitLinks(decoded)
            if (links.isNotEmpty()) return links
        }
        return emptyList()
    }

    private fun splitLinks(text: String): List<String> =
        text.split('\n', '\r')
            .map { it.trim() }
            .filter { it.isNotEmpty() && ShareLinkParser.isShareLink(it) }

    /**
     * Subscription payloads use standard or URL-safe base64, often unpadded.
     * Returns null when the input is not base64 at all.
     */
    private fun decodeBase64(text: String): String? {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return null
        if (compact.contains("://")) return null
        // java.util.Base64 rather than android.util.Base64 so this stays testable
        // on the JVM. Available since API 26, which is our floor.
        val normalised = compact.replace('-', '+').replace('_', '/')
        val padded = normalised.padEnd(normalised.length + (4 - normalised.length % 4) % 4, '=')
        val bytes = runCatching { Base64.getMimeDecoder().decode(padded) }.getOrNull() ?: return null
        val decoded = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        return decoded.takeIf { it.contains("://") }
    }

    private fun JsonElement.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() && it != "null" }

    private fun JsonObject.long(key: String): Long? {
        val raw = this[key] ?: return null
        val primitive = raw as? JsonPrimitive ?: return null
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        return primitive.contentOrNull?.toDoubleOrNull()?.toLong()
    }

    /** Accepts either an epoch number or an ISO-8601 instant. */
    private fun JsonObject.epochSeconds(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        val content = primitive.contentOrNull ?: return null
        content.toLongOrNull()?.let { value ->
            // Millisecond timestamps are past this bound; seconds are not.
            return if (value > 100_000_000_000L) value / 1000 else value
        }
        return runCatching { Instant.parse(content).epochSecond }.getOrNull()
    }
}
