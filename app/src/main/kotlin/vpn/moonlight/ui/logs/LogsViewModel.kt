package vpn.moonlight.ui.logs

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import vpn.moonlight.AppContainer
import vpn.moonlight.core.VpnController
import vpn.moonlight.core.xray.XrayLogFile
import vpn.moonlight.data.logging.LogEntry
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.ConnectionState

/** Which log the screen is showing. They are separate files with separate formats. */
enum class LogSource { App, Core }

data class LogsUiState(
    val entries: List<LogEntry> = emptyList(),
    val verbose: Boolean = false,
    val connection: ConnectionState = ConnectionState.Disconnected,
    val source: LogSource = LogSource.App,
)

class LogsViewModel(private val container: AppContainer) : ViewModel() {

    private val source = MutableStateFlow(LogSource.App)

    fun setSource(value: LogSource) {
        source.value = value
    }

    val state: StateFlow<LogsUiState> = combine(
        MoonlightLog.entries,
        container.settingsStore.settings,
        VpnController.state,
        source,
    ) { entries, settings, connection, selected ->
        // Newest first: a support log is read from the failure backwards.
        LogsUiState(
            entries = entries.asReversed(),
            verbose = settings.verboseLogging,
            connection = connection,
            source = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LogsUiState())

    fun setVerbose(enabled: Boolean) {
        viewModelScope.launch { container.settingsStore.setVerboseLogging(enabled) }
    }

    fun clear() = MoonlightLog.clear()

    /**
     * The core's log lives in a file rather than the app's buffer, because it is
     * written from inside Go. Read on demand, newest first.
     */
    suspend fun coreLines(context: Context): List<String> = withContext(Dispatchers.IO) {
        XrayLogFile.recentLines(context).asReversed()
    }

    /** Built off the main thread: it reads the core's log file. */
    suspend fun buildReport(context: Context): String = withContext(Dispatchers.IO) {
        LogExport.build(
            context = context,
            subscription = container.subscriptionRepository.subscription.value,
            state = VpnController.state.value,
            coreVersion = runCatching { container.xrayCore.version() }.getOrDefault("unknown"),
            verbose = state.value.verbose,
        )
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LogsViewModel(container) }
        }
    }
}
