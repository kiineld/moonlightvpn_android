package vpn.moonlight.data.local

import android.content.Context
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.flow.first

/**
 * The identity sent to the panel for its device limit.
 *
 * The HWID is a **random UUID minted once and stored locally** — deliberately not
 * `ANDROID_ID`, the advertising ID, or anything hardware-derived. It still gives
 * the panel a stable per-install handle for enforcing a device cap, while
 * carrying no hardware identity off the device. Clearing app data mints a new one,
 * which is the correct trade: a user who wipes the app looks like a new device.
 */
class DeviceIdentity(
    private val context: Context,
    private val store: SettingsStore,
) {
    suspend fun hardwareId(): String {
        store.hardwareId.first()?.let { return it }
        val minted = UUID.randomUUID().toString()
        store.setHardwareId(minted)
        return minted
    }

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
