package vpn.moonlight.ui.auth

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
import kotlinx.serialization.json.JsonObject
import vpn.moonlight.AppContainer
import vpn.moonlight.R
import vpn.moonlight.data.model.AuthTokens
import vpn.moonlight.data.model.LegalDoc
import vpn.moonlight.data.model.TelegramChallenge
import vpn.moonlight.ui.common.UiText
import vpn.moonlight.ui.common.toUiText

enum class AuthMode { SignIn, SignUp, Code, Forgot }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SignIn,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val acceptTerms: Boolean = false,
    val acceptData: Boolean = false,
    val busy: Boolean = false,
    val error: UiText? = null,
    val notice: UiText? = null,
    /** Set while Telegram's OAuth page is open. */
    val telegram: TelegramChallenge? = null,
    val legal: List<LegalDoc> = emptyList(),
) {
    private val emailOk get() = email.trim().let { '@' in it && '.' in it.substringAfter('@') }

    val canSubmit: Boolean
        get() = !busy && when (mode) {
            AuthMode.SignIn -> emailOk && password.isNotEmpty()
            AuthMode.SignUp -> emailOk && password.isNotEmpty() && acceptTerms && acceptData
            AuthMode.Code -> code.trim().length >= 4
            AuthMode.Forgot -> emailOk
        }
}

class AuthViewModel(private val container: AppContainer) : ViewModel() {

    private val account = container.accountRepository
    private val api = account.api

    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { api.legal() }.onSuccess { docs -> _state.update { it.copy(legal = docs) } }
        }
    }

    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null, notice = null, code = "") }
    fun setEmail(value: String) = _state.update { it.copy(email = value, error = null) }
    fun setPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun setCode(value: String) = _state.update { it.copy(code = value.filter(Char::isDigit).take(8), error = null) }
    fun setAcceptTerms(value: Boolean) = _state.update { it.copy(acceptTerms = value) }
    fun setAcceptData(value: Boolean) = _state.update { it.copy(acceptData = value) }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        val email = s.email.trim()
        when (s.mode) {
            AuthMode.SignIn -> work { finish(api.login(email, s.password)) }
            AuthMode.SignUp -> work {
                val result = api.register(email, s.password)
                if (result.isComplete) finish(result) else _state.update { it.copy(mode = AuthMode.Code, code = "") }
            }
            AuthMode.Code -> work { finish(api.verifyEmail(email, s.code.trim())) }
            AuthMode.Forgot -> work {
                api.forgotPassword(email)
                _state.update { it.copy(mode = AuthMode.SignIn, notice = UiText.Res(R.string.auth_forgot_sent)) }
            }
        }
    }

    fun resendCode() = work {
        api.resendCode(_state.value.email.trim())
        _state.update { it.copy(notice = UiText.Res(R.string.auth_code_resent)) }
    }

    fun startTelegram() = work {
        val challenge = api.telegramChallenge()
        _state.update { it.copy(telegram = challenge) }
    }

    fun cancelTelegram() = _state.update { it.copy(telegram = null) }

    fun telegramFailed() =
        _state.update { it.copy(telegram = null, error = UiText.Res(R.string.auth_telegram_failed)) }

    /** Telegram's page answered with an ID token, or with the classic widget fields. */
    fun completeTelegram(idToken: String?, widget: JsonObject?) {
        val challenge = _state.value.telegram ?: return
        _state.update { it.copy(telegram = null) }
        work { finish(api.completeTelegram(challenge.token, idToken, widget)) }
    }

    private suspend fun finish(tokens: AuthTokens) {
        if (!tokens.isComplete) error("incomplete session")
        // The account loads behind the switch to the main screens; a failure there
        // shows on Home with a retry, not here.
        account.onSignedIn()
    }

    private fun work(block: suspend () -> Unit) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null, notice = null) }
        viewModelScope.launch {
            runCatching { block() }.onFailure { e -> _state.update { it.copy(error = e.toUiText()) } }
            _state.update { it.copy(busy = false) }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { AuthViewModel(container) }
        }
    }
}
