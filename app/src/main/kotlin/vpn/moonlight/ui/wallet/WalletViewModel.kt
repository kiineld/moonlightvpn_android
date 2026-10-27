package vpn.moonlight.ui.wallet

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
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.model.Transaction
import vpn.moonlight.ui.common.UiText
import vpn.moonlight.ui.common.toUiText

data class WalletUiState(
    val account: Account? = null,
    val transactions: List<Transaction> = emptyList(),
    /** Rubles, as typed. */
    val amount: String = "500",
    val method: String? = null,
    val promo: String = "",
    val busy: Boolean = false,
    val error: UiText? = null,
    val message: UiText? = null,
    /** A provider's page to open for the top-up just created. */
    val paymentUrl: String? = null,
) {
    val amountKopecks: Long? get() = amount.toLongOrNull()?.takeIf { it in 100..100_000 }?.times(100)

    /** The chosen method, else the first the server offers. */
    val selectedMethod: String? get() = method ?: account?.paymentMethods?.firstOrNull()?.id

    val canTopUp: Boolean get() = !busy && amountKopecks != null && selectedMethod != null
}

class WalletViewModel(container: AppContainer) : ViewModel() {

    private val repository = container.accountRepository
    private val api = repository.api
    private val local = MutableStateFlow(WalletUiState())

    val state: StateFlow<WalletUiState> = combine(local, repository.account) { ui, account -> ui.copy(account = account) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WalletUiState())

    /** On every return: a top-up paid in the browser lands while the app was away. */
    fun refresh() {
        viewModelScope.launch {
            repository.refresh()
            runCatching { api.transactions() }.onSuccess { list -> local.update { it.copy(transactions = list) } }
        }
    }

    fun setAmount(value: String) = local.update { it.copy(amount = value.filter(Char::isDigit).take(6), error = null) }
    fun setMethod(id: String) = local.update { it.copy(method = id) }
    fun setPromo(value: String) = local.update { it.copy(promo = value.trim().take(64)) }
    fun paymentOpened() = local.update { it.copy(paymentUrl = null) }
    fun messageShown() = local.update { it.copy(message = null) }

    fun topUp() {
        val s = state.value
        val amount = s.amountKopecks ?: return
        val method = s.selectedMethod ?: return
        if (!s.canTopUp) return
        work {
            val result = api.topUp(amount, method)
            local.update { it.copy(paymentUrl = result.link) }
        }
    }

    fun applyPromo() {
        val code = state.value.promo
        if (code.isEmpty()) return
        work {
            val message = api.activatePromo(code)
            local.update { it.copy(promo = "", message = UiText.Server(message)) }
            refresh()
        }
    }

    private fun work(block: suspend () -> Unit) {
        if (local.value.busy) return
        local.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> local.update { it.copy(error = e.toUiText()) } }
            local.update { it.copy(busy = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { WalletViewModel(container) }
        }
    }
}
