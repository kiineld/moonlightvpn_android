package vpn.moonlight.data.remote

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import vpn.moonlight.data.local.DeviceIdentity
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.ServerNode
import vpn.moonlight.data.model.Subscription
import vpn.moonlight.data.model.SubscriptionUserInfo

/** Why a subscription fetch failed, in terms the UI can act on. */
sealed interface SubscriptionFetchError {
    data object Network : SubscriptionFetchError
    data object NotFound : SubscriptionFetchError
    data object DeviceLimitReached : SubscriptionFetchError
    data object Unauthorized : SubscriptionFetchError
    data object NoNodes : SubscriptionFetchError
    data class Http(val code: Int) : SubscriptionFetchError
    data class Unknown(val message: String?) : SubscriptionFetchError
}

class SubscriptionFetchException(val error: SubscriptionFetchError) : Exception(error.toString())

/**
 * Fetches a subscription from the panel.
 *
 * Tries the **JSON subscription** (`<url>/json`) first and falls back to the
 * base64 share-link body. This order matters and is not merely a preference:
 * Remnawave lets a host carry a raw XRAY JSON override — a balancer across a
 * dozen outbounds, its own DNS and routing — and the base64 format flattens
 * every host to one URI, silently discarding all of it. A host whose override is
 * a VLESS balancer appears in base64 as a single `ss://` placeholder that cannot
 * work. Both endpoints return the same `subscription-userinfo` and
 * `profile-title` headers, so nothing is lost by preferring JSON.
 */
class SubscriptionApi(
    private val deviceIdentity: DeviceIdentity,
    private val client: OkHttpClient = defaultClient(),
) {
    suspend fun fetch(url: String): Result<Subscription> = withContext(Dispatchers.IO) {
        val normalised = normaliseUrl(url)
            ?: return@withContext Result.failure(SubscriptionFetchException(SubscriptionFetchError.NotFound))

        var lastError: SubscriptionFetchError? = null

        // 1. JSON subscription — full per-node configs.
        execute(jsonUrl(normalised)).fold(
            onSuccess = { raw ->
                val nodes = PanelConfigParser.parse(raw.body)
                MoonlightLog.i(TAG, "json subscription: ${raw.body.length} bytes, ${nodes.size} nodes")
                if (nodes.isNotEmpty()) {
                    return@withContext Result.success(raw.toSubscription(normalised, nodes))
                }
                MoonlightLog.w(TAG, "json subscription had no usable configs; trying base64")
            },
            onFailure = {
                lastError = (it as? SubscriptionFetchException)?.error
                MoonlightLog.w(TAG, "json subscription failed ($lastError); trying base64")
            },
        )

        // 2. Base64 / plain share links.
        execute(normalised).fold(
            onSuccess = { raw ->
                val parsed = SubscriptionBodyParser.parse(raw.body)
                val nodes = ShareLinkParser.parseAll(parsed.links)
                MoonlightLog.i(TAG, "base64 subscription: ${parsed.links.size} links, ${nodes.size} nodes")
                if (nodes.isEmpty()) {
                    return@withContext Result.failure(
                        SubscriptionFetchException(lastError ?: SubscriptionFetchError.NoNodes),
                    )
                }
                Result.success(
                    raw.toSubscription(normalised, nodes, bodyTitle = parsed.title, bodyInfo = parsed.userInfo),
                )
            },
            onFailure = { failure ->
                Result.failure(
                    SubscriptionFetchException(
                        (failure as? SubscriptionFetchException)?.error
                            ?: lastError
                            ?: SubscriptionFetchError.Network,
                    ),
                )
            },
        )
    }

    /** A response reduced to the parts that matter, so the body can outlive the call. */
    private class RawResponse(
        val body: String,
        val userInfoHeader: String?,
        val title: String?,
        val announce: String?,
        val updateIntervalHours: Int?,
        val supportUrl: String?,
        val webPageUrl: String?,
    ) {
        fun toSubscription(
            url: String,
            nodes: List<ServerNode>,
            bodyTitle: String? = null,
            bodyInfo: SubscriptionUserInfo? = null,
        ) = Subscription(
            url = url,
            title = title ?: bodyTitle,
            nodes = nodes,
            // The header is what every panel implements consistently, so it wins
            // field by field; the body only fills gaps.
            userInfo = mergeUserInfo(SubscriptionUserInfoHeader.parse(userInfoHeader), bodyInfo),
            announce = announce,
            updateIntervalHours = updateIntervalHours,
            supportUrl = supportUrl,
            webPageUrl = webPageUrl,
            fetchedAtEpochSeconds = System.currentTimeMillis() / 1000,
        )

        private fun mergeUserInfo(
            header: SubscriptionUserInfo?,
            body: SubscriptionUserInfo?,
        ): SubscriptionUserInfo? {
            if (header == null) return body
            if (body == null) return header
            return SubscriptionUserInfo(
                uploadBytes = header.uploadBytes ?: body.uploadBytes,
                downloadBytes = header.downloadBytes ?: body.downloadBytes,
                totalBytes = header.totalBytes ?: body.totalBytes,
                expiresAtEpochSeconds = header.expiresAtEpochSeconds ?: body.expiresAtEpochSeconds,
                deviceLimit = header.deviceLimit ?: body.deviceLimit,
                devicesUsed = header.devicesUsed ?: body.devicesUsed,
            )
        }
    }

    private suspend fun execute(url: String): Result<RawResponse> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Moonlight/${deviceIdentity.appVersion} (Android)")
            .header("Accept", "application/json, text/plain, */*")
            .header("x-hwid", deviceIdentity.hardwareId())
            .header("x-device-os", "Android")
            .header("x-ver-os", deviceIdentity.osVersion)
            .header("x-device-model", deviceIdentity.deviceModel)
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    MoonlightLog.w(TAG, "GET $url -> HTTP ${response.code}")
                    return Result.failure(SubscriptionFetchException(response.toError()))
                }
                Result.success(
                    RawResponse(
                        body = response.body?.string().orEmpty(),
                        userInfoHeader = response.header("subscription-userinfo"),
                        title = ProfileTitle.decode(response.header("profile-title")),
                        // `announce` arrives with the same base64: prefix as the title.
                        announce = ProfileTitle.decode(response.header("announce")),
                        updateIntervalHours = response.header("profile-update-interval")?.toIntOrNull(),
                        supportUrl = response.header("support-url"),
                        webPageUrl = response.header("profile-web-page-url"),
                    ),
                )
            }
        } catch (e: IOException) {
            MoonlightLog.w(TAG, "GET $url failed", e)
            Result.failure(SubscriptionFetchException(SubscriptionFetchError.Network))
        }
    }

    private fun Response.toError(): SubscriptionFetchError = when (code) {
        401, 403 -> SubscriptionFetchError.Unauthorized
        404, 410 -> SubscriptionFetchError.NotFound
        // Remnawave answers a device-cap breach with 429.
        429 -> SubscriptionFetchError.DeviceLimitReached
        in 500..599 -> SubscriptionFetchError.Network
        else -> SubscriptionFetchError.Http(code)
    }

    /** Accepts a bare host or a full URL. Share links are not subscriptions. */
    private fun normaliseUrl(input: String): String? {
        val trimmed = input.trim().removeSuffix("/")
        if (trimmed.isEmpty()) return null
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            ShareLinkParser.isShareLink(trimmed) -> null
            trimmed.contains('.') -> "https://$trimmed"
            else -> null
        }
    }

    internal fun jsonUrl(base: String): String = when {
        base.endsWith("/json", ignoreCase = true) -> base
        else -> "$base/json"
    }

    companion object {
        private const val TAG = "Subscription"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
