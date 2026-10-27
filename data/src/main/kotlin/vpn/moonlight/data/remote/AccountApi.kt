package vpn.moonlight.data.remote

import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.model.AuthTokens
import vpn.moonlight.data.model.CheckoutResult
import vpn.moonlight.data.model.LegalDoc
import vpn.moonlight.data.model.Tariff
import vpn.moonlight.data.model.TelegramChallenge
import vpn.moonlight.data.model.Transaction
import vpn.moonlight.data.model.TrialInfo

/**
 * A failed call. [status] is the HTTP status, or 0 when the server was never
 * reached; [message] is the server's own text when it sent one, which is
 * already worded for the person reading it.
 */
class AccountApiException(val status: Int, message: String) : Exception(message) {
    /** The server said the session is over — nothing else ends one. */
    val isSessionRejected: Boolean get() = status == 401
}

/** Where the session's refresh token lives; the access token is only kept in memory. */
interface SessionTokens {
    suspend fun refreshToken(): String?
    suspend fun save(tokens: AuthTokens)
    suspend fun clear()
}

/**
 * The moonlight backend, the same `/api/v1` the web cabinet calls.
 *
 * An expired access token is refreshed once, behind a lock, and the request
 * retried — every screen's request gets its 401 in the same instant, and one
 * refresh each would be a burst into the server's rate limit. Only a 401 on the
 * refresh itself ends the session: a 5xx or a dropped connection says the server
 * is busy, and the same refresh token works again a moment later.
 */
class AccountApi(
    baseUrl: String,
    private val tokens: SessionTokens,
    private val userAgent: String,
    private val client: OkHttpClient = SubscriptionApi.defaultClient(),
    /** Called once the server has rejected the session. */
    private val onSessionRejected: suspend () -> Unit = {},
) {
    private val base = baseUrl.trimEnd('/')
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; explicitNulls = false }
    private val refreshLock = Mutex()

    @Volatile private var accessToken: String? = null

    // ── Sign-in ────────────────────────────────────────────────────────────

    suspend fun login(email: String, password: String): AuthTokens =
        signIn("/auth/login", buildJsonObject { put("email", email); put("password", password) })

    /** Tokens at once, or [AuthTokens.status] `verification_required` when a code was emailed. */
    suspend fun register(email: String, password: String): AuthTokens =
        signIn(
            "/auth/register",
            buildJsonObject {
                put("email", email)
                put("password", password)
                // The screen only enables sign-up once both are ticked.
                put("accept_terms", true)
                put("accept_personal_data", true)
            },
        )

    suspend fun verifyEmail(email: String, code: String): AuthTokens =
        signIn("/auth/verify", buildJsonObject { put("email", email); put("code", code) })

    suspend fun resendCode(email: String) {
        call<JsonElement>("POST", "/auth/resend", buildJsonObject { put("email", email) }, authenticated = false)
    }

    suspend fun forgotPassword(email: String) {
        call<JsonElement>("POST", "/auth/password/forgot", buildJsonObject { put("email", email) }, authenticated = false)
    }

    /** The offer, terms and consent documents a sign-up agrees to. */
    suspend fun legal(): List<LegalDoc> = call<LegalBody>("GET", "/legal", authenticated = false).docs

    suspend fun telegramChallenge(): TelegramChallenge =
        call("POST", "/auth/telegram/oidc/request", JsonObject(emptyMap()), authenticated = false)

    /** Completes a Telegram sign-in with the ID token Telegram's page returned, or its widget fields. */
    suspend fun completeTelegram(challengeToken: String, idToken: String?, widget: JsonObject?): AuthTokens =
        signIn(
            "/auth/telegram/oidc/complete",
            buildJsonObject {
                put("token", challengeToken)
                if (widget != null) put("widget", widget) else put("id_token", idToken)
            },
        )

    private suspend fun signIn(path: String, body: JsonObject): AuthTokens {
        val result = call<AuthTokens>("POST", path, body, authenticated = false)
        if (result.isComplete) adopt(result)
        return result
    }

    private suspend fun adopt(result: AuthTokens) {
        accessToken = result.accessToken
        tokens.save(result)
    }

    suspend fun signOut() {
        accessToken = null
        tokens.clear()
    }

    // ── The account ────────────────────────────────────────────────────────

    suspend fun me(): Account = call("GET", "/me")

    /** The subscription URL for one subscription — what the tunnel fetches its servers from. */
    suspend fun subscriptionLink(subscriptionId: Long): String =
        call<LinkBody>("GET", "/subscription/link?sub_id=$subscriptionId").link

    suspend fun tariffs(): List<Tariff> = call<TariffsBody>("GET", "/tariffs").tariffs

    suspend fun transactions(): List<Transaction> = call<TransactionsBody>("GET", "/me/transactions").transactions

    suspend fun trial(): TrialInfo = call("GET", "/trial")

    suspend fun activateTrial() {
        call<JsonElement>("POST", "/trial", JsonObject(emptyMap()))
    }

    /** The server's message on success ("Промокод активирован…"). */
    suspend fun activatePromo(code: String): String =
        call<MessageBody>("POST", "/promo", buildJsonObject { put("code", code) }).message

    /**
     * A new subscription. [requestId] makes a retry of the same order safe: the
     * server answers a repeated id with the first order rather than charging again.
     */
    suspend fun buy(tariffId: Long, days: Int, method: String, requestId: UUID): CheckoutResult =
        call(
            "POST",
            "/buy",
            buildJsonObject {
                put("tariff_id", tariffId)
                put("days", days)
                put("payment_method", method)
                put("request_id", requestId.toString())
            },
        )

    suspend fun renew(subscriptionId: Long, days: Int, method: String, requestId: UUID): CheckoutResult =
        call(
            "POST",
            "/subscription/$subscriptionId/renew",
            buildJsonObject {
                put("days", days)
                put("payment_method", method)
                put("request_id", requestId.toString())
            },
        )

    suspend fun topUp(amountKopecks: Long, method: String): CheckoutResult =
        call(
            "POST",
            "/topup",
            buildJsonObject {
                put("amount_kopecks", amountKopecks)
                put("payment_method", method)
            },
        )

    // ── Transport ──────────────────────────────────────────────────────────

    private suspend inline fun <reified T> call(
        method: String,
        path: String,
        body: JsonObject? = null,
        authenticated: Boolean = true,
    ): T = call(serializer(), method, path, body, authenticated)

    private suspend fun <T> call(
        type: KSerializer<T>,
        method: String,
        path: String,
        body: JsonObject?,
        authenticated: Boolean,
    ): T = withContext(Dispatchers.IO) {
        if (!authenticated) return@withContext json.decodeFromString(type, send(method, path, body, null))

        val token = accessToken ?: refreshAccess(null)
        try {
            json.decodeFromString(type, send(method, path, body, token))
        } catch (expired: AccountApiException) {
            if (!expired.isSessionRejected) throw expired
            json.decodeFromString(type, send(method, path, body, refreshAccess(token)))
        }
    }

    /**
     * A fresh access token. [stale] is the one that just failed: if another
     * request has already replaced it, that one is used instead of refreshing again.
     */
    private suspend fun refreshAccess(stale: String?): String = refreshLock.withLock {
        accessToken?.takeIf { it != stale }?.let { return it }
        val refresh = tokens.refreshToken() ?: throw AccountApiException(401, "Not signed in")
        val result = try {
            json.decodeFromString(
                AuthTokens.serializer(),
                send("POST", "/auth/refresh", buildJsonObject { put("refresh_token", refresh) }, null),
            )
        } catch (rejected: AccountApiException) {
            if (rejected.isSessionRejected) {
                accessToken = null
                tokens.clear()
                onSessionRejected()
            }
            throw rejected
        }
        if (!result.isComplete) throw AccountApiException(502, "Incomplete session from server")
        adopt(result)
        result.accessToken
    }

    private fun send(method: String, path: String, body: JsonObject?, token: String?): String {
        val request = Request.Builder()
            .url(base + path)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            // Payments return to the website rather than to the Telegram bot.
            .header("X-Cabinet-Client", "web")
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .method(method, body?.toString()?.toRequestBody(JSON_TYPE))
            .build()
        try {
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    MoonlightLog.w(TAG, "$method ${path.substringBefore('?')} -> HTTP ${response.code}")
                    throw AccountApiException(response.code, errorText(text) ?: "HTTP ${response.code}")
                }
                return text
            }
        } catch (e: IOException) {
            MoonlightLog.w(TAG, "$method ${path.substringBefore('?')} failed", e)
            throw AccountApiException(0, e.message ?: "network error")
        }
    }

    private fun errorText(body: String): String? = runCatching {
        ((json.parseToJsonElement(body) as JsonObject)["error"] as JsonPrimitive).contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

    @Serializable private data class LinkBody(val link: String)
    @Serializable private data class TariffsBody(val tariffs: List<Tariff> = emptyList())
    @Serializable private data class TransactionsBody(val transactions: List<Transaction> = emptyList())
    @Serializable private data class MessageBody(val message: String = "")
    @Serializable private data class LegalBody(val docs: List<LegalDoc> = emptyList())

    companion object {
        private const val TAG = "AccountApi"
        private val JSON_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
