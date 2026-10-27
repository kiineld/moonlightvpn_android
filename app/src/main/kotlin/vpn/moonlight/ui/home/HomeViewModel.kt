package vpn.moonlight.ui.home

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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.R
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.model.AccountSubscription
import vpn.moonlight.data.model.TrialInfo
import vpn.moonlight.ui.common.UiText
import vpn.moonlight.ui.common.toUiText

data class HomeUiState(
    val account: Account? = null,
    /** The subscription this phone tunnels through. */
    val activeId: Long? = null,
    val loading: Boolean = false,
    /** The subscription being switched to, while its servers load. */
    val switching: Long? = null,
    val trial: TrialInfo? = null,
    val error: UiText? = null,
    val message: UiText? = null,
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.accountRepository
    private val local = MutableStateFlow(HomeUiState())

    val state: StateFlow<HomeUiState> = combine(
        local,
        repository.account,
        repository.activeSubscriptionId,
    ) { ui, account, active -> ui.copy(account = account, activeId = active) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** On every return to the screen: a payment finished in the browser shows up here. */
    fun refresh() {
        if (local.value.loading) return
        local.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.refresh()
                .onFailure { e -> local.update { it.copy(error = e.toUiText()) } }
                .onSuccess { account ->
                    // Offered only to someone with nothing yet.
                    if (account.subscriptions.isEmpty() && !account.trialUsed) {
                        runCatching { repository.api.trial() }.onSuccess { t -> local.update { it.copy(trial = t) } }
                    }
                }
            local.update { it.copy(loading = false) }
        }
    }

    fun use(subscription: AccountSubscription) {
        if (local.value.switching != null) return
        local.update { it.copy(switching = subscription.id, message = null) }
        viewModelScope.launch {
            repository.use(subscription)
                .onFailure { local.update { it.copy(message = UiText.Res(R.string.home_switch_failed)) } }
            local.update { it.copy(switching = null) }
        }
    }

    fun activateTrial() {
        viewModelScope.launch {
            runCatching { repository.api.activateTrial() }
                .onSuccess {
                    local.update { it.copy(trial = null, message = UiText.Res(R.string.home_trial_activated)) }
                    refresh()
                }
                .onFailure { e -> local.update { it.copy(message = e.toUiText()) } }
        }
    }

    fun messageShown() = local.update { it.copy(message = null) }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
