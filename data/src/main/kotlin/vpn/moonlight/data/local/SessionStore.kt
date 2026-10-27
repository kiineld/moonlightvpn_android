package vpn.moonlight.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import vpn.moonlight.data.model.AuthTokens
import vpn.moonlight.data.remote.SessionTokens

private val Context.session: DataStore<Preferences> by preferencesDataStore(name = "moonlight_session")

/**
 * The signed-in session: the refresh token and which of the account's
 * subscriptions this phone uses.
 *
 * App-private storage, which the manifest keeps out of backups
 * (`allowBackup="false"`), so the token never leaves the device that earned it.
 */
class SessionStore(private val context: Context) : SessionTokens {

    private object Keys {
        val refreshToken = stringPreferencesKey("refresh_token")
        val activeSubscription = longPreferencesKey("active_subscription")
    }

    val isSignedIn: Flow<Boolean> = context.session.data.map { it[Keys.refreshToken] != null }

    val activeSubscriptionId: Flow<Long?> = context.session.data.map { it[Keys.activeSubscription] }

    override suspend fun refreshToken(): String? = context.session.data.first()[Keys.refreshToken]

    override suspend fun save(tokens: AuthTokens) {
        context.session.edit { it[Keys.refreshToken] = tokens.refreshToken }
    }

    override suspend fun clear() {
        context.session.edit { it.clear() }
    }

    suspend fun setActiveSubscription(id: Long) {
        context.session.edit { it[Keys.activeSubscription] = id }
    }
}
