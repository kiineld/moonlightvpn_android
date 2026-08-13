package vpn.moonlight.ui.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.data.model.AppLanguage
import vpn.moonlight.data.model.AppSettings
import vpn.moonlight.data.model.SplitMode
import vpn.moonlight.data.model.ThemeMode

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val selectedAppCount: Int = 0,
    /** From the panel's `support-url` header, when it sent one. */
    val supportUrl: String? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SettingsUiState> = combine(
        container.settingsStore.settings,
        container.subscriptionRepository.subscription,
    ) { settings, subscription ->
        SettingsUiState(
            settings = settings,
            selectedAppCount = settings.splitPackages.size,
            supportUrl = subscription?.supportUrl,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsStore.setTheme(mode) }
    }

    fun setLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.settingsStore.setLanguage(language)
            // Applied through AppCompat so it also works below API 33, where
            // LocaleManager does not exist. This recreates the activity.
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.tag))
        }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setNotificationsEnabled(enabled) }
    }

    fun setSplitMode(mode: SplitMode) {
        viewModelScope.launch { container.settingsStore.setSplitMode(mode) }
    }

    fun resetOnboarding() {
        viewModelScope.launch { container.settingsStore.setOnboardingComplete(false) }
    }

    fun completeOnboarding() {
        viewModelScope.launch { container.settingsStore.setOnboardingComplete(true) }
    }

    fun coreVersion(): String = container.xrayCore.version()

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
