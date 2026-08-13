package vpn.moonlight.data.logging

/**
 * Strips secrets out of anything destined for a shared log.
 *
 * Logs from a VPN client are exactly the wrong place for credentials, and the
 * whole point of the log screen is that people send them to someone else. A
 * subscription URL *is* a credential — it hands over the entire node list — and
 * so is the UUID in a `vless://` link. Both are masked, while leaving enough
 * shape behind to tell one host or node apart from another.
 */
object LogRedactor {

    private val uuid = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
    )

    /** `scheme://credential@host` — the credential half of any share link. */
    private val shareLinkCredential = Regex("""([a-zA-Z0-9+.-]+)://[^\s/@]+@""")

    /** The opaque path of a subscription URL, which is the secret part of it. */
    private val subscriptionPath = Regex("""(https?://[^\s/]+)/[^\s"']{6,}""")

    /** Long base64-ish blobs: encoded configs, vmess payloads, keys. */
    private val longBlob = Regex("""[A-Za-z0-9+/_-]{40,}={0,2}""")

    private val queryCredential = Regex(
        """([?&](?:pbk|sid|password|pass|key|secret|token|sni)=)[^&\s"']+""",
        RegexOption.IGNORE_CASE,
    )

    fun redact(text: String): String = text
        .replace(uuid, "<uuid>")
        .replace(shareLinkCredential) { "${it.groupValues[1]}://<credential>@" }
        .replace(queryCredential) { "${it.groupValues[1]}<redacted>" }
        .replace(subscriptionPath) { "${it.groupValues[1]}/<redacted>" }
        .replace(longBlob, "<blob>")
}
