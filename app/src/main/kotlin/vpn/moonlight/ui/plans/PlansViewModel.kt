package vpn.moonlight.ui.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import vpn.moonlight.AppContainer
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.model.AccountSubscription
import vpn.moonlight.data.model.Pricing
import vpn.moonlight.data.model.Tariff
import vpn.moonlight.ui.common.UiText
import vpn.moonlight.ui.common.toUiText

/** What paying produced: a provider's page to open, or a finished order. */
sealed interface PlansOutcome {
    data class OpenPayment(val url: String) : PlansOutcome
    data object Done : PlansOutcome
}

data class PlansUiState(
    val account: Account? = null,
    /** Set when renewing; null when buying a new subscription. */
    val renewing: AccountSubscription? = null,
    val tariffs: List<Tariff> = emptyList(),
    val tariffId: Long? = null,
    val days: Int = 30,
    /** What the user picked: `balance`, or a provider id from [Account.paymentMethods]. */
    val chosenMethod: String? = null,
    val busy: Boolean = false,
    val error: UiText? = null,
    val outcome: PlansOutcome? = null,
) {
    val tariff: Tariff? get() = tariffs.firstOrNull { it.id == tariffId }

    private val per30: Long? get() = renewing?.renewalPer30 ?: tariff?.pricePer30

    /** The quote, discount included, before any provider fee. */
    val price: Long?
        get() = per30?.let { Pricing.discounted(Pricing.forPeriod(it, days), account?.discount?.percent ?: 0) }

    /** The balance when it covers the order, else the first provider — until the user picks. */
    val method: String
        get() = chosenMethod ?: run {
            val enough = price != null && (account?.balance ?: 0) >= price!!
            if (enough) BALANCE else account?.paymentMethods?.firstOrNull()?.id ?: BALANCE
        }

    val balanceShort: Boolean get() = method == BALANCE && price != null && (account?.balance ?: 0) < price!!

    val canPay: Boolean get() = !busy && price != null && !balanceShort

    companion object {
        const val BALANCE = "balance"
    }
}

class PlansViewModel(container: AppContainer, renewId: Long?) : ViewModel() {

    private val repository = container.accountRepository
    private val api = repository.api
    private val local = MutableStateFlow(PlansUiState())

    /**
     * One request id per distinct order, kept for the life of the screen: tapping
     * Pay again for the same order after a timeout replays it rather than charging
     * twice, while a different order gets its own.
     */
    private val requestIds = mutableMapOf<String, UUID>()

    val state: StateFlow<PlansUiState> = combine(local, repository.account) { ui, account ->
        ui.copy(account = account, renewing = renewId?.let { id -> account?.subscriptions?.firstOrNull { it.id == id } })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlansUiState())

    init {
        if (renewId == null) {
            viewModelScope.launch {
                runCatching { api.tariffs() }
                    .onSuccess { tariffs ->
                        val first = tariffs.firstOrNull { it.isPopular } ?: tariffs.firstOrNull()
                        local.update { it.copy(tariffs = tariffs, tariffId = first?.id) }
                    }
                    .onFailure { e -> local.update { it.copy(error = e.toUiText()) } }
            }
        }
    }

    fun selectTariff(id: Long) = local.update { it.copy(tariffId = id, error = null) }
    fun selectDays(days: Int) = local.update { it.copy(days = days, error = null) }
    fun selectMethod(method: String) = local.update { it.copy(chosenMethod = method, error = null) }
    fun outcomeHandled() = local.update { it.copy(outcome = null) }

    fun pay() {
        val s = state.value
        if (!s.canPay) return
        val renewing = s.renewing
        val tariffId = s.tariffId
        val key = listOf(renewing?.id, tariffId, s.days, s.method).joinToString("|")
        val requestId = requestIds.getOrPut(key) { UUID.randomUUID() }
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching {
                if (renewing != null) {
                    api.renew(renewing.id, s.days, s.method, requestId)
                } else {
                    api.buy(tariffId ?: error("no tariff"), s.days, s.method, requestId)
                }
            }
                .onSuccess { result ->
                    val link = result.link
                    val outcome = if (!link.isNullOrBlank()) PlansOutcome.OpenPayment(link) else PlansOutcome.Done
                    // A finished order is spent; the next Pay is a new order.
                    if (outcome == PlansOutcome.Done) requestIds.remove(key)
                    local.update { it.copy(outcome = outcome) }
                    repository.refresh()
                }
                .onFailure { e -> local.update { it.copy(error = e.toUiText()) } }
            local.update { it.copy(busy = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer, renewId: Long?): ViewModelProvider.Factory = viewModelFactory {
            initializer { PlansViewModel(container, renewId) }
        }
    }
}
