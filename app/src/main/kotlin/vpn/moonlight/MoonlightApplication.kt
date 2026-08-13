package vpn.moonlight

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import vpn.moonlight.core.TunnelDependencies
import vpn.moonlight.alerts.SubscriptionAlerts
import vpn.moonlight.core.TunnelDependenciesProvider
import vpn.moonlight.core.xray.XrayAssets
import vpn.moonlight.data.model.ThemeMode

class MoonlightApplication : Application(), TunnelDependenciesProvider {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val tunnelDependencies: TunnelDependencies by lazy {
        TunnelDependencies(
            xray = container.xrayCore,
            subscriptions = container.subscriptionRepository,
            settings = container.settingsStore,
            latencies = container.latencyRepository,
        )
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Set before any activity exists, so applying it cannot recreate one.
        // Its only job is to tell the framework which -night resources to use for
        // windows the *system* draws: the splash, and the starting window. A
        // theme change takes effect in Compose immediately and here on next launch.
        applyNightMode(container.themeStartupCache.theme)
        SubscriptionAlerts.ensureChannel(this)
        SubscriptionAlerts.schedule(this)

        scope.launch {
            // Mirrors the theme where it can be read synchronously at startup.
            container.settingsStore.settings.collect { settings ->
                container.themeStartupCache.theme = settings.theme
            }
        }

        scope.launch {
            // ~24 MB of geodata, copied off the main thread. Only has to finish
            // before a config is loaded, not before the environment is set.
            XrayAssets.extract(this@MoonlightApplication)

            // The cached subscription is loaded before anything asks for it, so
            // the first frame shows real nodes rather than an empty list.
            container.subscriptionRepository.loadCached()
        }
    }

    /**
     * Publishes the app's light/dark choice to the framework.
     *
     * This is what makes `-night` resources resolve for windows the *system*
     * draws, chiefly the splash. On API 31+ AppCompat routes it to
     * `UiModeManager.setApplicationNightMode`, which the system remembers, so the
     * splash is right before a single line of app code has run.
     *
     * Called only from onCreate. Calling it on a theme change would recreate the
     * activity, and Compose already repaints the theme without one.
     */
    private fun applyNightMode(theme: ThemeMode) {
        val mode = when (theme) {
            ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        if (AppCompatDelegate.getDefaultNightMode() != mode) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }

}
