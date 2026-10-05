package vpn.moonlight.data.local

import android.content.Context
import android.os.Build
import android.provider.Settings
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * The identity sent to the panel for its device limit.
 *
 * The HWID is **derived from the device**, so it is the same after an update,
 * after the app is deleted and installed again, and after its data is cleared:
 * the panel counts one phone as one device however often the app is
 * reinstalled. It used to be a random UUID minted on first launch and stored
 * with the settings, which every reinstall minted afresh — each one took
 * another slot of the device limit until there were none left.
 *
 * The source is `ANDROID_ID`, which Android keeps per device, per user and per
 * signing key, and which survives everything short of a factory reset. It is
 * hashed before it is sent; see [HardwareId].
 */
class DeviceIdentity(
    private val context: Context,
    private val store: SettingsStore,
) {
    suspend fun hardwareId(): String {
        platformId()?.let { return HardwareId.from(it) }

        // Only for a device that will not say who it is. A stored random UUID
        // is then the best there is: stable until the app's data is cleared.
        store.hardwareId.first()?.let { return it }
        val minted = UUID.randomUUID().toString()
        store.setHardwareId(minted)
        return minted
    }

    private fun platformId(): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
    }.getOrNull()?.takeIf(HardwareId::isUsable)

    val osVersion: String get() = Build.VERSION.RELEASE ?: Build.VERSION.SDK_INT.toString()

    val deviceModel: String
        get() {
            val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
            val model = Build.MODEL?.trim().orEmpty()
            return when {
                model.startsWith(manufacturer, ignoreCase = true) -> model
                manufacturer.isEmpty() -> model
                else -> "$manufacturer $model"
            }.ifBlank { "Android" }
        }

    val appVersion: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "1.0"
}
