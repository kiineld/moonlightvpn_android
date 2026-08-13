package vpn.moonlight.core.xray

import vpn.moonlight.data.logging.MoonlightLog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * [XrayCore] over the gomobile binding.
 *
 * The whole native surface is one call — `LibXray.invoke(requestJson)` — taking
 * `{"apiVersion":1,"method":…,"payload":…}` and returning
 * `{"success":…,"data":…,"error":…}`. Everything below is that envelope.
 */
class LibXrayCore : XrayCore {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = false }

    override fun version(): String =
        invoke("xrayVersion").getOrNull()
            ?.let { (it as? JsonObject)?.get("version")?.stringOrNull() }
            ?: "unknown"

    override fun isRunning(): Boolean =
        invoke("getXrayState").getOrNull()
            ?.let { (it as? JsonObject)?.get("running")?.let { v -> (v as? JsonPrimitive)?.booleanOrNull } }
            ?: false

    override fun convertShareLink(shareLink: String): Result<String> =
        invoke("convertShareLinksToXrayJson", buildJsonObject { put("text", shareLink) })
            .mapCatching { data ->
                data?.toString() ?: throw XrayException("core returned no config for the share link")
            }

    override fun start(configJson: String): Result<Unit> =
        invoke("runXrayFromJson", buildJsonObject { put("configJSON", configJson) }).map { }

    override fun stop(): Result<Unit> = invoke("stopXray").map { }

    override fun freePorts(count: Int): Result<List<Int>> =
        invoke("getFreePorts", buildJsonObject { put("count", count) })
            .mapCatching { data ->
                val ports = (data as? JsonObject)?.get("ports") as? JsonArray
                    ?: throw XrayException("core returned no ports")
                ports.mapNotNull { (it as? JsonPrimitive)?.intOrNull }
            }

    override fun registerSocketProtector(protect: (Int) -> Boolean) {
        runCatching {
            val controller = libXray.DialerController { fd -> protect(fd.toInt()) }
            libXray.LibXray.registerDialerController(controller)
            libXray.LibXray.registerListenerController(controller)
        }.onFailure { MoonlightLog.e(TAG, "could not register socket protector", it) }
    }

    override fun setDns(server: String, protect: (Int) -> Boolean): Result<Unit> = runCatching {
        libXray.LibXray.setDNS(libXray.DialerController { fd -> protect(fd.toInt()) }, server)
    }

    override fun resetDns() {
        runCatching { libXray.LibXray.resetDNS() }
            .onFailure { MoonlightLog.w(TAG, "could not reset DNS", it) }
    }

    /** Wraps one `invoke` round trip: build the envelope, unwrap the response. */
    private fun invoke(method: String, payload: JsonObject? = null): Result<JsonObject?> {
        val request = buildJsonObject {
            put("apiVersion", API_VERSION)
            put("method", method)
            if (payload != null) put("payload", payload)
        }

        val raw = runCatching { libXray.LibXray.invoke(request.toString()) }
            .getOrElse { return Result.failure(XrayException("native call '$method' failed: ${it.message}", it)) }

        val response = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: return Result.failure(XrayException("core returned unparseable JSON for '$method'"))

        val success = (response["success"] as? JsonPrimitive)?.booleanOrNull ?: false
        if (!success) {
            val error = (response["error"] as? JsonPrimitive)?.contentOrNull ?: "unknown error"
            MoonlightLog.w(TAG, "$method failed: $error")
            return Result.failure(XrayException("$method: $error"))
        }
        return Result.success(response["data"] as? JsonObject)
    }

    private fun kotlinx.serialization.json.JsonElement.stringOrNull(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private companion object {
        const val TAG = "LibXrayCore"
        const val API_VERSION = 1
    }
}

class XrayException(message: String, cause: Throwable? = null) : Exception(message, cause)
