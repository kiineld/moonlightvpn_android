package vpn.moonlight.data.local

import android.content.Context
import vpn.moonlight.data.model.ThemeMode

/**
 * The chosen theme, mirrored somewhere it can be read synchronously.
 *
 * The window background has to be set before the first frame is drawn, and
 * DataStore — the real source of truth — can only be read asynchronously. So the
 * theme is mirrored into SharedPreferences purely as a startup hint.
 *
 * This matters because changing the app language recreates the activity: for the
 * moment between the old window going away and the new one drawing, the system
 * paints `windowBackground`. Left as a fixed dark colour, that is a black flash
 * every time a light-theme user switches language.
 *
 * Deliberately not a second source of truth: it is written from the settings
 * flow and only ever read to pick a colour before Compose starts.
 */
class ThemeStartupCache(context: Context) {

    private val preferences =
        context.getSharedPreferences("moonlight_startup", Context.MODE_PRIVATE)

    var theme: ThemeMode
        get() = preferences.getString(KEY, null)
            ?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
            ?: ThemeMode.Dark
        set(value) {
            // Committed, not applied: an activity may be recreated immediately
            // after, and an async write can lose the race with the next window.
            preferences.edit().putString(KEY, value.name).commit()
        }

    private companion object {
        const val KEY = "theme"
    }
}
