package vpn.moonlight.data.remote

import vpn.moonlight.data.model.SubscriptionUserInfo

/**
 * Parses the de-facto standard `subscription-userinfo` header:
 *
 * ```
 * upload=1024; download=2048; total=107374182400; expire=1788000000
 * ```
 *
 * Unknown keys are ignored and unparseable values are dropped rather than
 * defaulted, so a malformed field reads as "unknown" instead of zero.
 */
object SubscriptionUserInfoHeader {

    fun parse(header: String?): SubscriptionUserInfo? {
        if (header.isNullOrBlank()) return null

        val fields = header.split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isEmpty()) return@mapNotNull null
                val separator = trimmed.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = trimmed.substring(0, separator).trim().lowercase()
                val value = trimmed.substring(separator + 1).trim()
                key to value
            }
            .toMap()

        if (fields.isEmpty()) return null

        val info = SubscriptionUserInfo(
            uploadBytes = fields["upload"]?.toLongOrNull(),
            downloadBytes = fields["download"]?.toLongOrNull(),
            totalBytes = fields["total"]?.toLongOrNull(),
            expiresAtEpochSeconds = fields["expire"]?.toLongOrNull(),
        )

        val hasAnything = info.uploadBytes != null || info.downloadBytes != null ||
            info.totalBytes != null || info.expiresAtEpochSeconds != null
        return info.takeIf { hasAnything }
    }
}
