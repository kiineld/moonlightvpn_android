package vpn.moonlight.data.remote

/** A release published on GitHub, reduced to what the updater needs. */
data class AppRelease(
    val versionName: String,
    val notes: String?,
    val asset: ReleaseAsset,
)

/** One downloadable file attached to a release. */
data class ReleaseAsset(
    val name: String,
    val url: String,
    val sizeBytes: Long,
)

/**
 * Orders release names.
 *
 * Tags here are plain `vX.Y.Z`, so only the leading numeric run is compared;
 * anything after it (`-beta`, build metadata) is ignored rather than ranked.
 * The point is that this is a numeric comparison: as strings, "1.0.10" sorts
 * *before* "1.0.9", which would strand everyone on the older build.
 */
object AppVersion {

    private val NUMERIC = Regex("""\d+(?:\.\d+)*""")

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    fun compare(a: String, b: String): Int {
        val left = parts(a)
        val right = parts(b)
        repeat(maxOf(left.size, right.size)) { i ->
            val diff = left.getOrElse(i) { 0 }.compareTo(right.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    private fun parts(version: String): List<Int> =
        NUMERIC.find(version.trim().removePrefix("v"))
            ?.value
            ?.split('.')
            ?.mapNotNull(String::toIntOrNull)
            .orEmpty()
}

/**
 * Chooses which APK to download for this device.
 *
 * Preferring the device's own ABI over the universal build is worth the branch:
 * the split APKs are around 35 MB where the universal one is over 110 MB, and
 * the difference is native code for architectures the phone cannot run.
 */
object ReleaseAssets {

    fun forAbis(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
        supportedAbis.forEach { abi ->
            assets.firstOrNull { it.name.endsWith("-$abi.apk", ignoreCase = true) }
                ?.let { return it }
        }
        return assets.firstOrNull { it.name.endsWith("-universal.apk", ignoreCase = true) }
            ?: assets.firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
    }
}
