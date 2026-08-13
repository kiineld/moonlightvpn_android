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

    private fun applyLanguage(tag: String) {
        val current = AppCompatDelegate.getApplicationLocales()
        if (current.toLanguageTags() == tag) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
}
