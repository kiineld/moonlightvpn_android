package vpn.moonlight.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The account as the moonlight backend (`/api/v1`) describes it — the same API
 * the web cabinet uses. Only the fields the app shows are declared; the rest are
 * ignored, and every field has a default so an older or newer server still parses.
 * Money is always in kopecks, as the server sends it.
 */

@Serializable
data class AuthTokens(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    /** `verification_required` when a sign-up waits for the emailed code. */
    val status: String? = null,
    val email: String? = null,
) {
    val isComplete: Boolean get() = accessToken.isNotEmpty() && refreshToken.isNotEmpty()
}

/** `offer`, `terms`, `privacy` or `consent`, with where to read it. */
@Serializable
data class LegalDoc(val key: String = "", val title: String = "", val url: String = "")

/** What Telegram's OAuth page needs, issued by the backend for one sign-in. */
@Serializable
data class TelegramChallenge(
    val token: String,
    val nonce: String,
    @SerialName("client_id") val clientId: String,
    @SerialName("expires_in") val expiresInSeconds: Int = 300,
)

@Serializable
data class Account(
    val id: Long = 0,
    val username: String? = null,
    @SerialName("first_name") val firstName: String? = null,
    val email: String? = null,
    @SerialName("telegram_id") val telegramId: Long? = null,
    @SerialName("telegram_linked") val telegramLinked: Boolean = false,
    val balance: Long = 0,
    @SerialName("balance_formatted") val balanceFormatted: String = "",
    @SerialName("trial_used") val trialUsed: Boolean = true,
    val subscriptions: List<AccountSubscription> = emptyList(),
    @SerialName("payment_methods") val paymentMethods: List<PaymentMethod> = emptyList(),
    val discount: Discount? = null,
) {
    /** What to call the person: their name, their @handle, or their email. */
    val displayName: String?
        get() = firstName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
            ?: email
}

@Serializable
data class AccountSubscription(
    val id: Long,
    val plan: String = "",
    val devices: Int = 0,
    @SerialName("traffic_gb") val trafficGb: Double = 0.0,
    @SerialName("used_gb") val usedGb: Double = 0.0,
    val status: String = "",
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("term_days") val termDays: Int? = null,
    @SerialName("has_link") val hasLink: Boolean = false,
    @SerialName("tariff_id") val tariffId: Long? = null,
    @SerialName("price_per_30_kopecks") val pricePer30: Long = 0,
    @SerialName("renew_price_per_30_kopecks") val renewPricePer30: Long? = null,
    @SerialName("panel_sync_pending") val syncPending: Boolean = false,
) {
    val isActive: Boolean get() = status == "active"

    /** Zero traffic means no cap. */
    val isUnlimitedTraffic: Boolean get() = trafficGb <= 0.0

    /** 30 days of renewal, as the server will charge it. */
    val renewalPer30: Long get() = renewPricePer30 ?: pricePer30
}

@Serializable
data class Discount(val percent: Int = 0)

@Serializable
data class PaymentMethod(
    /** `yookassa`, `lava` or `heleket`; `balance` is never listed, it is always there. */
    val id: String,
    val name: String = "",
    @SerialName("fee_percent") val feePercent: Double = 0.0,
)

@Serializable
data class Tariff(
    val id: Long,
    val name: String = "",
    val description: String = "",
    val devices: Int = 0,
    @SerialName("traffic_gb") val trafficGb: Double = 0.0,
    /** The 30-day price. */
    @SerialName("price_kopecks") val pricePer30: Long = 0,
    @SerialName("original_price_kopecks") val originalPricePer30: Long = 0,
    @SerialName("has_sale") val hasSale: Boolean = false,
    @SerialName("is_popular") val isPopular: Boolean = false,
)

@Serializable
data class Transaction(
    val id: Long,
    @SerialName("amount_kopecks") val amount: Long = 0,
    @SerialName("amount_formatted") val amountFormatted: String = "",
    val type: String = "",
    val description: String = "",
    val status: String = "",
    @SerialName("created_at") val createdAt: String = "",
)

@Serializable
data class TrialInfo(
    val enabled: Boolean = false,
    val days: Int = 0,
    val devices: Int = 0,
    @SerialName("traffic_gb") val trafficGb: Double = 0.0,
    @SerialName("can_use") val canUse: Boolean = false,
)

/** The answer to a purchase, a renewal or a top-up. */
@Serializable
data class CheckoutResult(
    val ok: Boolean = false,
    /** `pending` until paid, then `completed`; absent for an immediate result. */
    val status: String? = null,
    /** The provider's payment page, when the order is paid outside the balance. */
    val link: String? = null,
    @SerialName("amount_formatted") val amountFormatted: String? = null,
    @SerialName("total_formatted") val totalFormatted: String? = null,
)

/**
 * The server's period ladder: days → multiplier on the 30-day rate. Mirrors
 * `checkout/quote.go` and the cabinet's `PERIOD_MULTIPLIERS`; the server
 * re-prices every order, so this is for display only.
 */
object Pricing {
    val periods: List<Int> = listOf(30, 90, 180, 365)

    private val multipliers = mapOf(30 to 1.0, 90 to 2.7, 180 to 5.0, 365 to 9.0)

    /** Truncated, as the server's `int(rate * mult)` is. */
    fun forPeriod(pricePer30: Long, days: Int): Long =
        (pricePer30 * (multipliers[days] ?: 0.0)).toLong()

    /** Floored percentage off, as the server applies it. */
    fun discounted(kopecks: Long, percent: Int): Long =
        if (percent <= 0) kopecks else (kopecks - kopecks * percent.coerceAtMost(100) / 100).coerceAtLeast(0)

    fun rubles(kopecks: Long): String = "${kopecks / 100} ₽"
}
