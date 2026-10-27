package vpn.moonlight.data.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import vpn.moonlight.data.local.SessionStore
import vpn.moonlight.data.logging.MoonlightLog
import vpn.moonlight.data.model.Account
import vpn.moonlight.data.model.AccountSubscription
import vpn.moonlight.data.remote.AccountApi

/**
 * The signed-in account, and which of its subscriptions this phone tunnels through.
 *
 * Choosing a subscription fetches its link from the backend and hands that to
 * [SubscriptionRepository] — from there on the tunnel works exactly as it did when
 * a link was pasted in, including offline from the cached servers.
 */
class AccountRepository(
    val api: AccountApi,
    private val session: SessionStore,
    private val subscriptions: SubscriptionRepository,
) {
    /** Null until the stored session has been read. */
    private val _signedIn = MutableStateFlow<Boolean?>(null)
    val signedIn: StateFlow<Boolean?> = _signedIn.asStateFlow()

    private val _account = MutableStateFlow<Account?>(null)
    val account: StateFlow<Account?> = _account.asStateFlow()

    val activeSubscriptionId = session.activeSubscriptionId

    suspend fun restore() {
        // The cached servers first: [refresh] compares against them.
        if (subscriptions.subscription.value == null) subscriptions.loadCached()
        _signedIn.value = session.refreshToken() != null
    }

    /** After any successful sign-in: the tokens are already stored by [AccountApi]. */
    suspend fun onSignedIn(): Result<Account> {
        _signedIn.value = true
        return refresh()
    }

    /** Called by [AccountApi] when the server rejects the session. */
    fun onSessionRejected() {
        _account.value = null
        _signedIn.value = false
    }

    suspend fun signOut() {
        runCatching { api.signOut() }
        subscriptions.clear()
        _account.value = null
        _signedIn.value = false
    }

    /**
     * Reloads the account. When this phone has no subscription chosen yet, or the
     * chosen one is gone, the first active one is picked so a new user is one tap
     * from connecting.
     */
    suspend fun refresh(): Result<Account> = runCatching { api.me() }
        .onSuccess { account ->
            _account.value = account
            val chosen = session.activeSubscriptionId.first()
            val stillThere = account.subscriptions.any { it.id == chosen && it.hasLink }
            if (!stillThere || subscriptions.subscription.value == null) {
                val pick = account.subscriptions.firstOrNull { it.id == chosen && it.hasLink }
                    ?: account.subscriptions.firstOrNull { it.isActive && it.hasLink }
                if (pick != null) use(pick)
            }
        }
        .onFailure { MoonlightLog.w(TAG, "could not load the account", it) }

    /** Makes [subscription] the one this phone connects through. */
    suspend fun use(subscription: AccountSubscription): Result<Unit> = runCatching {
        val link = api.subscriptionLink(subscription.id)
        subscriptions.import(link).getOrThrow()
        session.setActiveSubscription(subscription.id)
    }.onFailure { MoonlightLog.w(TAG, "could not switch to subscription ${subscription.id}", it) }

    private companion object {
        const val TAG = "Account"
    }
}
