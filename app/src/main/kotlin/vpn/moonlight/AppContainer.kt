package vpn.moonlight

import android.content.Context
import vpn.moonlight.core.xray.LibXrayCore
import vpn.moonlight.core.xray.XrayAssets
import vpn.moonlight.core.xray.WarmLatencyProbe
import vpn.moonlight.core.xray.XrayCore
import vpn.moonlight.data.local.DeviceIdentity
import vpn.moonlight.data.local.SettingsStore
import vpn.moonlight.data.local.ThemeStartupCache
import vpn.moonlight.data.local.SubscriptionStore
import vpn.moonlight.data.remote.SubscriptionApi
import vpn.moonlight.data.repository.InstalledAppsRepository
import vpn.moonlight.data.repository.LatencyRepository
import vpn.moonlight.data.repository.SubscriptionRepository

/**
 * Manual dependency wiring.
 *
 * The graph is a dozen objects deep at most, all singletons, all constructed
 * eagerly except the native core — which is worth the annotation processing a DI
 * framework would add only if the graph were much larger than this.
 */
class AppContainer(private val context: Context) {

    val settingsStore = SettingsStore(context)
    val themeStartupCache = ThemeStartupCache(context)
    val deviceIdentity = DeviceIdentity(context, settingsStore)
    val installedApps = InstalledAppsRepository(context)

    val latencyRepository = LatencyRepository()

    private val subscriptionApi = SubscriptionApi(deviceIdentity)
    private val subscriptionStore = SubscriptionStore(context)

    val subscriptionRepository = SubscriptionRepository(
        api = subscriptionApi,
        store = subscriptionStore,
        latencies = latencyRepository,
    )

    /** Lazy so the ~50 MB native library loads on first use, not at app start. */
    val xrayCore: XrayCore by lazy { LibXrayCore() }

    val latencyProbe by lazy {
        WarmLatencyProbe(
            xray = xrayCore,
            latencies = latencyRepository,
            assetDir = XrayAssets.directory(context).absolutePath,
        )
    }
}
