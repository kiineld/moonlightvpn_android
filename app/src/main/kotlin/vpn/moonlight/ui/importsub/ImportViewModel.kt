package vpn.moonlight.ui.importsub

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.R
import vpn.moonlight.data.model.Subscription
import vpn.moonlight.data.remote.SubscriptionFetchError
import vpn.moonlight.data.remote.SubscriptionFetchException

data class ImportUiState(
    val url: String = "",
    val isSubmitting: Boolean = false,
    val imported: Subscription? = null,
    @StringRes val errorRes: Int? = null,
) {
    val isDone: Boolean get() = imported != null
    val canSubmit: Boolean get() = url.isNotBlank() && !isSubmitting
}

class ImportViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState())
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    /**
     * Clears the form and any previous result.
     *
     * `viewModel()` resolves against the Activity's ViewModelStore keyed by class,
     * so this instance outlives the screen: navigating away and back returns the
     * *same* view model with `imported` still set from last time. The screen then
     * opened straight into the success state and claimed a subscription had been
     * added when nothing had — visibly wrong after deleting one and adding another,
     * and "fixed" only by restarting the process.
     */
    fun reset() {
        _state.value = ImportUiState()
    }

    fun onUrlChange(value: String) {
        _state.update { it.copy(url = value, errorRes = null) }
    }

    /** Submits whatever is in the field. */
    fun submit() = submit(_state.value.url)

    fun submit(rawUrl: String) {
        val url = rawUrl.trim()
        if (url.isBlank()) {
            _state.update { it.copy(errorRes = R.string.import_error_invalid) }
            return
        }
        if (_state.value.isSubmitting) return

        _state.update { it.copy(url = url, isSubmitting = true, errorRes = null) }
        viewModelScope.launch {
            container.subscriptionRepository.import(url).fold(
                onSuccess = { subscription ->
                    container.settingsStore.setOnboardingComplete(true)
                    _state.update { it.copy(isSubmitting = false, imported = subscription) }
                    // Latency is measured straight away so `Auto` has something
                    // to choose from on the very first connect.
                    container.latencyProbe.measureAll(subscription.nodes)
                },
                onFailure = { error ->
                    _state.update { it.copy(isSubmitting = false, errorRes = error.toMessageRes()) }
                },
            )
        }
    }

    /** Takes a subscription URL off the clipboard, ignoring anything else on it. */
    fun submitFromClipboard(clipboardText: String?) {
        val candidate = clipboardText?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("http://") || it.startsWith("https://") }

        if (candidate == null) {
            _state.update { it.copy(errorRes = R.string.import_error_clipboard) }
            return
        }
        submit(candidate)
    }

    @StringRes
    private fun Throwable.toMessageRes(): Int = when ((this as? SubscriptionFetchException)?.error) {
        SubscriptionFetchError.Network -> R.string.import_error_network
        SubscriptionFetchError.NotFound -> R.string.import_error_not_found
        SubscriptionFetchError.Unauthorized -> R.string.import_error_unauthorized
        SubscriptionFetchError.DeviceLimitReached -> R.string.import_error_device_limit
        SubscriptionFetchError.NoNodes -> R.string.import_error_no_nodes
        else -> R.string.import_error_invalid
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { ImportViewModel(container) }
        }
    }
}
