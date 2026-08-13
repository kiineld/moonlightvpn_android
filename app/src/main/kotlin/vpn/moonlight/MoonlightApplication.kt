package vpn.moonlight

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
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
        SubscriptionAlerts.ensureChannel(this)
        SubscriptionAlerts.schedule(this)

        scope.launch {
            // Mirrors the theme so the next activity can colour its window before
            // the first frame, which is what removes the flash on a language change.
            container.settingsStore.settings.collect { settings ->
                container.themeStartupCache.theme = settings.theme
                applyNightMode(settings.theme)
            }
        }

        scope.launch {
            // ~24 MB of geodata, copied off the main thread. Only has to finish
            // before a config is loaded, not before the environment is set.
            XrayAssets.extract(this@MoonlightApplication)

            // The cached subscription is loaded before anything asks for it, so
            // the first frame shows real nodes rather than an empty list.
            container.subscriptionRepository.loadCached()

            val settings = container.settingsStore.settings.first()
            applyLanguage(settings.language.tag)
        }
    }

    /**
     * Publishes the app's light/dark choice to the framework.
     *
     * This is what makes `-night` resources resolve for windows the *system*
     * draws — in particular the starting window shown while the app restarts
     * after a language change. On API 31+ AppCompat routes this to
     * `UiModeManager.setApplicationNightMode`, which the system remembers, so it
     * gets the background right before a single line of app code has run.
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

    private fun applyLanguage(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() == tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
