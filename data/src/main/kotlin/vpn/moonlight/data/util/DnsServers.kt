package vpn.moonlight.data.util

/**
 * Reads what the user typed as their DNS into the addresses Xray accepts: a bare
 * IP (port 53), or a URL whose scheme names the transport — `https://` for DNS
 * over HTTPS, `tcp://`, `quic+local://`. Several may be given, one per line or
 * separated by commas.
 *
 * Validated here because a value Xray rejects stops the tunnel from starting at
 * all, and the user would only find out on the next connect.
 */
object DnsServers {

    private val schemes = setOf("https", "https+local", "tcp", "tcp+local", "quic+local", "h2c")
    private val ipv4 = Regex("""^((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)\.){3}(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)$""")
    private val ipv6 = Regex("""^[0-9a-fA-F:.]+$""")
    private val host = Regex("""^[A-Za-z0-9.-]+|\[[0-9a-fA-F:.]+]$""")

    /** The addresses, or null when there are none or any one of them is not an address. */
    fun parse(input: String): List<String>? {
        val parts = input.split(',', '\n', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty() || parts.size > 8) return null
        return parts.takeIf { it.all(::isAddress) }
    }

    private fun isAddress(value: String): Boolean {
        if (ipv4.matches(value)) return true
        if (':' in value && "://" !in value) return ipv6.matches(value) && value.count { it == ':' } >= 2
        val scheme = value.substringBefore("://", missingDelimiterValue = "")
        if (scheme !in schemes) return false
        val authority = value.substringAfter("://").substringBefore('/')
        val hostPart = if (authority.startsWith("[")) authority.substringBefore("]") + "]" else authority.substringBefore(':')
        val port = authority.removePrefix(hostPart).removePrefix(":")
        return hostPart.isNotEmpty() && host.matches(hostPart) && (port.isEmpty() || port.toIntOrNull() in 1..65535)
    }
}
