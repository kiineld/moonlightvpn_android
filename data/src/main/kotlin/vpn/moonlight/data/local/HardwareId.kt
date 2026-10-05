package vpn.moonlight.data.local

import java.security.MessageDigest
import java.util.Locale

/**
 * Turns the platform's device identifier into the HWID the panel sees.
 *
 * The platform's own identifier never leaves the device. What is sent is a hash
 * of it with this app's salt: stable for this device, meaningless anywhere else,
 * and not the value other software on the device can read. The same scheme the
 * macOS client uses, so one service sees one shape of HWID from both.
 */
object HardwareId {

    private const val SALT = "moonlight-vpn/hwid/"

    /**
     * Every device that shipped with one notorious build of Android 2.2 reports
     * this same `ANDROID_ID`. Long gone, but an identifier shared between devices
     * is exactly what a device limit must not be handed.
     */
    private const val SHARED_ANDROID_ID = "9774d56d682e549c"

    /** Whether [platformId] identifies a device at all. */
    fun isUsable(platformId: String?): Boolean {
        val id = platformId?.trim().orEmpty()
        if (id.isEmpty() || id.equals(SHARED_ANDROID_ID, ignoreCase = true)) return false
        return id.any { it != '0' }
    }

    /** A salted SHA-256 of [platformId], laid out as a UUID like the random ones it replaces. */
    fun from(platformId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest((SALT + platformId.trim().uppercase(Locale.ROOT)).toByteArray())
        val hex = digest.take(16).joinToString("") { "%02X".format(it) }
        return listOf(
            hex.substring(0, 8),
            hex.substring(8, 12),
            hex.substring(12, 16),
            hex.substring(16, 20),
            hex.substring(20, 32),
        ).joinToString("-")
    }
}
