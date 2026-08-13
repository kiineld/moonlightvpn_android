package vpn.moonlight.ui.split

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.data.model.InstalledApp
import vpn.moonlight.data.model.SplitMode

data class AppRow(val app: InstalledApp, val isSelected: Boolean) {
    /** Matches on the label and the package, so "telegram" and "org.tele" both work. */
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        val needle = query.trim()
        return app.label.contains(needle, ignoreCase = true) ||
            app.packageName.contains(needle, ignoreCase = true)
    }
}

data class SplitUiState(
    val mode: SplitMode = SplitMode.All,
    val apps: List<AppRow> = emptyList(),
    val isLoading: Boolean = true,
    val query: String = "",
) {
    val isFiltered: Boolean get() = query.isNotBlank()
}

class SplitTunnelViewModel(private val container: AppContainer) : ViewModel() {

    private val installed = MutableStateFlow<List<InstalledApp>?>(null)

    private val query = MutableStateFlow("")

    val state: StateFlow<SplitUiState> = combine(
        container.settingsStore.settings,
        installed,
        query,
    ) { settings, apps, search ->
        val rows = apps.orEmpty()
            .map { AppRow(it, settings.splitPackages.contains(it.packageName)) }
            .filter { it.matches(search) }

        SplitUiState(
            mode = settings.splitMode,
            apps = rows,
            isLoading = apps == null,
            query = search,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SplitUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    init {
        viewModelScope.launch { installed.value = container.installedApps.installedApps() }
    }

    fun setMode(mode: SplitMode) {
        viewModelScope.launch { container.settingsStore.setSplitMode(mode) }
    }

    fun toggleApp(packageName: String) {
        viewModelScope.launch {
            val current = state.value.apps.filter { it.isSelected }.map { it.app.packageName }.toSet()
            val next = if (packageName in current) current - packageName else current + packageName
            container.settingsStore.setSplitPackages(next)
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SplitTunnelViewModel(container) }
        }
    }
}
