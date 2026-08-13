package vpn.moonlight.data.remote

import java.net.URI
import java.net.URLDecoder
import java.security.MessageDigest
import vpn.moonlight.data.model.ServerNode

/**
 * Derives display metadata from a share link.
 *
 * Deliberately does **not** parse the cryptographic payload — Xray-core does
 * that via `convertShareLinksToXrayJson`. This only reads what the UI shows:
 * the remark, a flag, the host, and a protocol label.
 */
object ShareLinkParser {

    private val supportedSchemes = setOf("vless", "vmess", "trojan", "ss", "socks", "hysteria2", "hy2", "tuic")

    fun isShareLink(text: String): Boolean {
        val scheme = text.trim().substringBefore("://", missingDelimiterValue = "").lowercase()
        return scheme in supportedSchemes
    }

    fun parseAll(links: List<String>): List<ServerNode> =
        links.mapNotNull { parse(it) }.distinctBy { it.id }

    fun parse(rawLink: String): ServerNode? {
        val link = rawLink.trim()
        if (!isShareLink(link)) return null

        val scheme = link.substringBefore("://").lowercase()
        val parsed = RemarkParser.parse(decodeRemark(link.substringAfter('#', "")))
        val (host, port) = hostAndPort(link)

        return ServerNode(
            id = stableId(link),
            name = parsed.name ?: host ?: scheme.uppercase(),
            panelConfigJson = null,
            shareLink = link,
            flag = parsed.flag,
            countryCode = parsed.countryCode,
            squad = parsed.squad,
            protocolLabel = protocolLabel(scheme, link),
            host = host,
            port = port,
        )
    }

    /** Sixteen hex characters of SHA-256 — stable across fetches, short enough to log. */
    private fun stableId(link: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(link.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }

    private fun decodeRemark(fragment: String): String =
        if (fragment.isEmpty()) {
            ""
        } else {
            runCatching { URLDecoder.decode(fragment, "UTF-8") }.getOrDefault(fragment)
        }

    private fun hostAndPort(link: String): Pair<String?, Int?> {
        // vmess:// carries a base64 blob rather than an authority, so URI parsing
        // yields nothing useful for it. That is fine: host is display-only here.
        val uri = runCatching { URI(link) }.getOrNull() ?: return null to null
        val host = uri.host?.takeIf { it.isNotBlank() }
        val port = uri.port.takeIf { it > 0 }
        return host to port
    }

    private fun protocolLabel(scheme: String, link: String): String {
        val query = link.substringAfter('?', "").substringBefore('#')
        val security = query.split('&')
            .firstOrNull { it.startsWith("security=") }
            ?.substringAfter('=')
            ?.lowercase()
        val base = when (scheme) {
            "vless" -> "VLESS"
            "vmess" -> "VMess"
            "trojan" -> "Trojan"
            "ss" -> "Shadowsocks"
            "hysteria2", "hy2" -> "Hysteria2"
            "tuic" -> "TUIC"
            else -> scheme.uppercase()
        }
        return when (security) {
            "reality" -> "$base Reality"
            "tls" -> "$base TLS"
            else -> base
        }
    }
}
