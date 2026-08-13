package vpn.moonlight.data.model

import kotlinx.serialization.Serializable

/** A subscription as last fetched from the panel, cached verbatim on disk. */
@Serializable
data class Subscription(
    val url: String,
    val title: String? = null,
    val nodes: List<ServerNode> = emptyList(),
    val userInfo: SubscriptionUserInfo? = null,
    val announce: String? = null,
    val updateIntervalHours: Int? = null,
    /** From the panel's `support-url` header — better than a hardcoded default. */
    val supportUrl: String? = null,
    /** From `profile-web-page-url`: the customer's cabinet, for renewals. */
    val webPageUrl: String? = null,
    val fetchedAtEpochSeconds: Long = 0L,
) {
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun node(id: String): ServerNode? = nodes.firstOrNull { it.id == id }
}

/**
 * Quota and expiry, from the `subscription-userinfo` response header or the
 * panel's JSON body. Every field is nullable on purpose: a panel that omits one
 * should render as "unknown", never as zero.
 */
@Serializable
data class SubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expiresAtEpochSeconds: Long? = null,
    val deviceLimit: Int? = null,
    val devicesUsed: Int? = null,
) {
    val usedBytes: Long?
        get() = when {
            uploadBytes == null && downloadBytes == null -> null
            else -> (uploadBytes ?: 0L) + (downloadBytes ?: 0L)
        }

    /** Panels signal "no cap" with a missing or zero total. */
    val isUnlimitedTraffic: Boolean get() = totalBytes == null || totalBytes <= 0L

    val usedFraction: Float?
        get() {
            val used = usedBytes ?: return null
            val total = totalBytes ?: return null
            if (total <= 0L) return null
            return (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)
        }

    val isPerpetual: Boolean get() = expiresAtEpochSeconds == null || expiresAtEpochSeconds <= 0L

    /**
     * Whole days remaining, rounded up so a subscription with six hours left
     * reads "1 day" rather than "0".
     */
    fun daysLeft(nowEpochSeconds: Long): Int? {
        val expiry = expiresAtEpochSeconds ?: return null
        if (expiry <= 0L) return null
        val remaining = expiry - nowEpochSeconds
        if (remaining <= 0L) return 0
        return ((remaining + SECONDS_PER_DAY - 1) / SECONDS_PER_DAY).toInt()
    }

    fun isExpired(nowEpochSeconds: Long): Boolean {
        val expiry = expiresAtEpochSeconds ?: return false
        return expiry in 1..nowEpochSeconds
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
