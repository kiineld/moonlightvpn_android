package vpn.moonlight.ui.subscription

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
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.NodeSelection
import vpn.moonlight.data.model.Subscription
import vpn.moonlight.data.repository.RefreshState

data class SubscriptionUiState(
    val subscription: Subscription? = null,
    val refresh: RefreshState = RefreshState.Idle,
    val nowEpochSeconds: Long = 0L,
) {
    val hasSubscription: Boolean get() = subscription != null
    val isRefreshing: Boolean get() = refresh is RefreshState.Refreshing
    val daysLeft: Int? get() = subscription?.userInfo?.daysLeft(nowEpochSeconds)
    val isExpired: Boolean get() = subscription?.userInfo?.isExpired(nowEpochSeconds) == true
}

class SubscriptionViewModel(private val container: AppContainer) : ViewModel() {

    val state: StateFlow<SubscriptionUiState> = combine(
        container.subscriptionRepository.subscription,
        container.subscriptionRepository.refreshState,
    ) { subscription, refresh ->
        SubscriptionUiState(
            subscription = subscription,
            refresh = refresh,
            nowEpochSeconds = System.currentTimeMillis() / 1000,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SubscriptionUiState())

    fun refresh() {
        viewModelScope.launch { container.subscriptionRepository.refresh() }
    }

    /**
     * Forgets the subscription entirely. The pinned node goes back to Auto too —
     * a node id from a deleted subscription would otherwise linger and resolve to
     * nothing on the next import.
     */
    fun delete() {
        viewModelScope.launch {
            container.subscriptionRepository.clear()
            container.settingsStore.setSelection(NodeSelection.Auto)
            container.latencyRepository.clear()
            MoonlightLog.i("Subscription", "subscription deleted by the user")
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SubscriptionViewModel(container) }
        }
    }
}
