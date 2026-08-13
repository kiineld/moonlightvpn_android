package vpn.moonlight.data.remote

import java.util.Base64

/**
 * Decodes the `profile-title` response header.
 *
 * Headers are ASCII, so a panel with a non-ASCII plan name has to encode it.
 * Two encodings turn up in practice: Remnawave's `base64:<payload>` prefix, and
 * RFC 2047's `=?utf-8?B?<payload>?=`. Anything else is already plain text.
 */
object ProfileTitle {

    private val rfc2047 = Regex("""^=\?utf-8\?B\?(.*)\?=$""", RegexOption.IGNORE_CASE)

    fun decode(raw: String?): String? {
        val value = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null

        val payload = when {
            value.startsWith(PREFIX, ignoreCase = true) -> value.substring(PREFIX.length)
            else -> rfc2047.find(value)?.groupValues?.get(1)
        } ?: return value

        // A payload that will not decode is more useful shown verbatim than
        // dropped — at least the user can see what the panel sent.
        return decodeBase64(payload) ?: value
    }

    private fun decodeBase64(payload: String): String? {
        val compact = payload.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return null
        val normalised = compact.replace('-', '+').replace('_', '/')
        val padded = normalised.padEnd(normalised.length + (4 - normalised.length % 4) % 4, '=')
        val bytes = runCatching { Base64.getMimeDecoder().decode(padded) }.getOrNull() ?: return null
        return runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private const val PREFIX = "base64:"
}
