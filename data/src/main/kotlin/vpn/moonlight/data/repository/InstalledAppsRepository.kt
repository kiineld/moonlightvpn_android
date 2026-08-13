package vpn.moonlight.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vpn.moonlight.data.model.InstalledApp

/**
 * The app list for the split-tunnel screen.
 *
 * Filtered to packages that actually hold `INTERNET`, which is both the correct
 * set to show and a narrower use of the package list than enumerating everything
 * installed.
 */
class InstalledAppsRepository(private val context: Context) {

    suspend fun installedApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val packageManager = context.packageManager
        val self = context.packageName

        packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
            .asSequence()
            .filter { it.packageName != self }
            .filter { it.requestedPermissions?.contains(android.Manifest.permission.INTERNET) == true }
            .mapNotNull { info ->
                val appInfo = info.applicationInfo ?: return@mapNotNull null
                InstalledApp(
                    packageName = info.packageName,
                    label = packageManager.getApplicationLabel(appInfo).toString(),
                    isSystem = appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
            // User-installed apps first, then alphabetical — the order someone
            // scanning for their banking app expects.
            .sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
            .toList()
    }
}
