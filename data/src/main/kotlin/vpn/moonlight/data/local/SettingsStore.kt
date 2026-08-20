package vpn.moonlight.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import vpn.moonlight.data.model.AppLanguage
import vpn.moonlight.data.model.AppSettings
import vpn.moonlight.data.model.NodeSelection
import vpn.moonlight.data.model.SplitMode
import vpn.moonlight.data.model.ThemeMode

private val Context.preferences: DataStore<Preferences> by preferencesDataStore(name = "moonlight_settings")

/** Preference persistence. One flow of [AppSettings] out, one setter per field in. */
class SettingsStore(private val context: Context) {

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val language = stringPreferencesKey("language")
        val notifications = booleanPreferencesKey("notifications")
        val splitMode = stringPreferencesKey("split_mode")
        val splitPackages = stringSetPreferencesKey("split_packages")
        val selectedNode = stringPreferencesKey("selected_node")
        val onboardingComplete = booleanPreferencesKey("onboarding_complete")
        val hardwareId = stringPreferencesKey("hardware_id")
        val verboseLogging = booleanPreferencesKey("verbose_logging")
    }

    val settings: Flow<AppSettings> = context.preferences.data.map { prefs ->
        AppSettings(
            theme = prefs[Keys.theme]?.toThemeMode() ?: ThemeMode.Dark,
            language = prefs[Keys.language]?.toLanguage() ?: AppLanguage.Russian,
            notificationsEnabled = prefs[Keys.notifications] ?: true,
            splitMode = prefs[Keys.splitMode]?.toSplitMode() ?: SplitMode.All,
            splitPackages = prefs[Keys.splitPackages] ?: emptySet(),
            selection = prefs[Keys.selectedNode]
                ?.let { NodeSelection.Pinned(it) }
                ?: NodeSelection.Auto,
            onboardingComplete = prefs[Keys.onboardingComplete] ?: false,
            verboseLogging = prefs[Keys.verboseLogging] ?: false,
        )
    }

    val hardwareId: Flow<String?> = context.preferences.data.map { it[Keys.hardwareId] }

    suspend fun setHardwareId(value: String) = edit { it[Keys.hardwareId] = value }

    suspend fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }

    suspend fun setLanguage(language: AppLanguage) = edit { it[Keys.language] = language.name }

    suspend fun setNotificationsEnabled(enabled: Boolean) = edit { it[Keys.notifications] = enabled }

    suspend fun setSplitMode(mode: SplitMode) = edit { it[Keys.splitMode] = mode.name }

    /**
     * Flips one package in the split-tunnelling selection.
     *
     * A toggle rather than a whole-set write, deliberately. The screen filters its
     * rows by a search query, so the selection it can see is not the selection
     * that exists; rebuilding the set from the visible rows and storing that
     * silently dropped every app the search had hidden. Reading the stored set
     * here means the caller never needs — or gets — the chance.
     *
     * DataStore serialises edit blocks, so two quick taps cannot lose each other
     * to a read-modify-write race either.
     */
    suspend fun toggleSplitPackage(packageName: String) = edit { prefs ->
        val current = prefs[Keys.splitPackages] ?: emptySet()
        prefs[Keys.splitPackages] =
            if (packageName in current) current - packageName else current + packageName
    }

    suspend fun setSelection(selection: NodeSelection) = edit { prefs ->
        when (selection) {
            NodeSelection.Auto -> prefs.remove(Keys.selectedNode)
            is NodeSelection.Pinned -> prefs[Keys.selectedNode] = selection.nodeId
        }
    }

    suspend fun setVerboseLogging(enabled: Boolean) = edit { it[Keys.verboseLogging] = enabled }

    suspend fun setOnboardingComplete(complete: Boolean) =
        edit { it[Keys.onboardingComplete] = complete }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.preferences.edit(block)
    }

    // Unknown persisted values fall back to the default rather than crashing —
    // an enum can lose a constant between releases.
    private fun String.toThemeMode() = ThemeMode.entries.firstOrNull { it.name == this }
    private fun String.toLanguage() = AppLanguage.entries.firstOrNull { it.name == this }
    private fun String.toSplitMode() = SplitMode.entries.firstOrNull { it.name == this }
}
