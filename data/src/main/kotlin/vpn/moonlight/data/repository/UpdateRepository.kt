package vpn.moonlight.data.repository

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.remote.AppRelease
import vpn.moonlight.data.remote.AppVersion
import vpn.moonlight.data.remote.UpdateApi
import vpn.moonlight.data.remote.UpdateError
import vpn.moonlight.data.remote.UpdateException

/** Where the updater is in the check → download → install sequence. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: AppRelease) : UpdateState
    data class Downloading(val release: AppRelease, val fraction: Float) : UpdateState
    data class Ready(val release: AppRelease, val file: File) : UpdateState
    data class Failed(val error: UpdateError) : UpdateState
}

/**
 * Checks GitHub for a newer build and downloads it.
 *
 * Installing is left to the caller: it needs a package installer and a content
 * URI, neither of which belong in the data layer.
 */
class UpdateRepository(
    private val api: UpdateApi,
    private val currentVersion: String,
    private val supportedAbis: List<String>,
    private val downloadDir: File,
    /**
     * Answers whether a downloaded APK is signed by the same key as the running
     * app. A mismatch cannot install — the system rejects it with a bare "app
     * not installed" — so it is worth catching here where it can be explained.
     */
    private val signedLikeInstalled: (File) -> Boolean,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    suspend fun check() {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking

        api.latest(supportedAbis).fold(
            onSuccess = { release ->
                _state.value = if (AppVersion.isNewer(release.versionName, currentVersion)) {
                    MoonlightLog.i(TAG, "update available: $currentVersion -> ${release.versionName}")
                    UpdateState.Available(release)
                } else {
                    MoonlightLog.i(TAG, "up to date at $currentVersion (latest ${release.versionName})")
                    UpdateState.UpToDate
                }
            },
            onFailure = { _state.value = UpdateState.Failed(it.asUpdateError()) },
        )
    }

    suspend fun download(release: AppRelease) {
        _state.value = UpdateState.Downloading(release, 0f)
        val destination = File(downloadDir, release.asset.name)

        api.download(release.asset, destination) { fraction ->
            // Ignore progress from a download the user has already cancelled.
            val current = _state.value
            if (current is UpdateState.Downloading && current.release == release) {
                _state.value = current.copy(fraction = fraction)
            }
        }.fold(
            onSuccess = { file ->
                _state.value = if (signedLikeInstalled(file)) {
                    UpdateState.Ready(release, file)
                } else {
                    MoonlightLog.w(TAG, "downloaded apk is signed by a different key; refusing to install")
                    file.delete()
                    UpdateState.Failed(UpdateError.SignatureMismatch)
                }
            },
            onFailure = { _state.value = UpdateState.Failed(it.asUpdateError()) },
        )
    }

    /** Drops back to Idle, e.g. when the user dismisses an error. */
    fun reset() {
        _state.value = UpdateState.Idle
    }

    /** Removes downloaded APKs; the newest one is tens of megabytes of cache. */
    fun clearDownloads() {
        runCatching { downloadDir.listFiles()?.forEach { it.delete() } }
    }

    private fun Throwable.asUpdateError(): UpdateError =
        (this as? UpdateException)?.error ?: UpdateError.Network

    private companion object {
        const val TAG = "Update"
    }
}
