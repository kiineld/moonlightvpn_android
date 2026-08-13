package vpn.moonlight.data.remote

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import vpn.moonlight.data.logging.MoonlightLog
import kotlin.coroutines.coroutineContext

/** Why an update check or download failed, in terms the UI can act on. */
sealed interface UpdateError {
    data object Network : UpdateError
    /** The repository has no published release, or none with an APK. */
    data object NoRelease : UpdateError
    data object Storage : UpdateError
    /** The download is signed by a different key, so it could never install. */
    data object SignatureMismatch : UpdateError
    data class Http(val code: Int) : UpdateError
}

class UpdateException(val error: UpdateError) : Exception(error.toString())

/**
 * Reads the latest release from GitHub and downloads its APK.
 *
 * Uses the public releases API unauthenticated, which is rate limited per IP
 * (60/hour) — far above what a manual "check for updates" button can reach.
 */
class UpdateApi(
    private val repository: String,
    private val userAgent: String,
    private val client: OkHttpClient = defaultClient(),
    private val baseUrl: String = GITHUB_API,
) {

    suspend fun latest(supportedAbis: List<String>): Result<AppRelease> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", userAgent)
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    MoonlightLog.w(TAG, "releases/latest -> HTTP ${response.code}")
                    val error = if (response.code == 404) UpdateError.NoRelease else UpdateError.Http(response.code)
                    return@withContext Result.failure(UpdateException(error))
                }
                val body = response.body?.string().orEmpty()
                val release = parse(body, supportedAbis)
                    ?: return@withContext Result.failure(UpdateException(UpdateError.NoRelease))
                MoonlightLog.i(TAG, "latest release ${release.versionName}, asset ${release.asset.name}")
                Result.success(release)
            }
        } catch (e: IOException) {
            MoonlightLog.w(TAG, "update check failed", e)
            Result.failure(UpdateException(UpdateError.Network))
        }
    }

    /**
     * Streams [asset] to [destination], reporting progress as a 0..1 fraction.
     *
     * Downloads to a `.part` file and renames on success, so an interrupted
     * download can never be mistaken for a complete APK on the next attempt.
     */
    suspend fun download(
        asset: ReleaseAsset,
        destination: File,
        onProgress: (Float) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(asset.url)
            .header("Accept", "application/octet-stream")
            .header("User-Agent", userAgent)
            .get()
            .build()

        val partial = File(destination.parentFile, destination.name + ".part")
        try {
            destination.parentFile?.mkdirs()
            partial.delete()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(UpdateException(UpdateError.Http(response.code)))
                }
                val body = response.body ?: return@withContext Result.failure(
                    UpdateException(UpdateError.Network),
                )
                val total = body.contentLength().takeIf { it > 0 } ?: asset.sizeBytes

                body.byteStream().use { source ->
                    partial.outputStream().use { sink ->
                        val buffer = ByteArray(DOWNLOAD_BUFFER)
                        var written = 0L
                        var reported = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = source.read(buffer)
                            if (read == -1) break
                            sink.write(buffer, 0, read)
                            written += read
                            if (total > 0) {
                                // Only on a whole percent, so a 35 MB download
                                // does not push thousands of recompositions.
                                val percent = (written * 100 / total).toInt()
                                if (percent != reported) {
                                    reported = percent
                                    onProgress(percent / 100f)
                                }
                            }
                        }
                    }
                }
            }

            destination.delete()
            if (!partial.renameTo(destination)) {
                return@withContext Result.failure(UpdateException(UpdateError.Storage))
            }
            MoonlightLog.i(TAG, "downloaded ${destination.name}, ${destination.length()} bytes")
            Result.success(destination)
        } catch (e: IOException) {
            partial.delete()
            MoonlightLog.w(TAG, "download failed", e)
            Result.failure(UpdateException(UpdateError.Network))
        } catch (e: Throwable) {
            partial.delete()
            throw e
        }
    }

    internal fun parse(body: String, supportedAbis: List<String>): AppRelease? {
        val root = runCatching { json.parseToJsonElement(body) as? JsonObject }.getOrNull() ?: return null
        if (root["draft"]?.text() == "true") return null

        val version = root["tag_name"]?.text() ?: root["name"]?.text() ?: return null
        val assets = (root["assets"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj["name"]?.text() ?: return@mapNotNull null
            val url = obj["browser_download_url"]?.text() ?: return@mapNotNull null
            ReleaseAsset(
                name = name,
                url = url,
                sizeBytes = (obj["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
            )
        }

        val asset = ReleaseAssets.forAbis(assets, supportedAbis) ?: return null
        return AppRelease(
            versionName = version.removePrefix("v"),
            notes = root["body"]?.text()?.takeIf { it.isNotBlank() },
            asset = asset,
        )
    }

    private fun kotlinx.serialization.json.JsonElement.text(): String? =
        (this as? JsonPrimitive)?.contentOrNull

    private fun JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> = this ?: emptyList()

    companion object {
        private const val TAG = "Update"
        private const val DOWNLOAD_BUFFER = 64 * 1024

        const val GITHUB_API = "https://api.github.com"

        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // An APK is tens of megabytes; a 30 s read timeout would fail on a
            // slow connection partway through a download that was progressing.
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
}
