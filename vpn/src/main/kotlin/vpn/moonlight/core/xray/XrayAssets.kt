package vpn.moonlight.core.xray

import android.content.Context
import vpn.moonlight.data.logging.MoonlightLog
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Makes `geoip.dat` and `geosite.dat` available to xray-core.
 *
 * These are not optional. Every config a Remnawave panel serves references
 * `geosite:` and `geoip:` rules — `category-ru`, `geoip:ru`, `youtube`, `vk` —
 * and the core refuses to load a config whose routing rules it cannot resolve.
 *
 * The location is handed to the core through the **config's root `env` object**
 * (`xray.location.asset`), not the process environment. Setting
 * `XRAY_LOCATION_ASSET` with `Os.setenv` looks like it should work and does not:
 * the value never reaches the Go runtime, and the core silently falls back to its
 * executable directory, failing with `stat /system/bin/geosite.dat`. libXray's own
 * docs are explicit that runtime options belong in the config.
 *
 * [extract] must finish before a config is loaded, which is why the service awaits
 * it rather than assuming the background copy from startup is done.
 */
object XrayAssets {

    private const val TAG = "XrayAssets"
    private const val ASSET_DIR = "geo"
    private val FILES = listOf("geoip.dat", "geosite.dat")

    /** Bumped when the bundled geodata changes, to force a re-extract on upgrade. */
    private const val VERSION = 1

    fun directory(context: Context): File = File(context.filesDir, ASSET_DIR)

    suspend fun extract(context: Context): Boolean = withContext(Dispatchers.IO) {
        val dir = directory(context)
        dir.mkdirs()
        val marker = File(dir, ".version")

        val upToDate = marker.takeIf { it.exists() }?.readText()?.trim() == VERSION.toString() &&
            FILES.all { File(dir, it).length() > 0 }
        if (upToDate) return@withContext true
        MoonlightLog.i(TAG, "extracting geodata (${FILES.joinToString()})")

        val copied = FILES.all { name ->
            runCatching {
                context.assets.open("$ASSET_DIR/$name").use { input ->
                    // Via a temp file then rename, so a kill mid-copy cannot leave a
                    // truncated .dat that the core rejects on next launch.
                    val temp = File(dir, "$name.tmp")
                    temp.outputStream().use(input::copyTo)
                    temp.renameTo(File(dir, name))
                }
            }.onFailure { MoonlightLog.e(TAG, "could not extract $name", it) }.isSuccess
        }

        if (copied) {
            runCatching { marker.writeText(VERSION.toString()) }
            MoonlightLog.i(
                TAG,
                "geodata ready: " + FILES.joinToString { "$it ${File(dir, it).length() / 1024} KB" },
            )
        }
        copied
    }

    fun isReady(context: Context): Boolean {
        val dir = directory(context)
        return FILES.all { File(dir, it).length() > 0 }
    }
}
