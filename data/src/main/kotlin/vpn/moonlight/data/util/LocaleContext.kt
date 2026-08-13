package vpn.moonlight.data.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import java.util.Locale

/**
 * A context whose resources resolve in the given language.
 *
 * Used instead of `AppCompatDelegate.setApplicationLocales`, which routes to the
 * system LocaleManager on API 33+ and **restarts the app** to apply a language.
 * That restart is what produced the black flash: the system paints its own
 * transition backdrop — pure black, not the app's window background — while the
 * process comes back.
 *
 * Overriding the configuration instead keeps everything in one process, so a
 * language change is an ordinary recomposition.
 */
fun Context.localizedFor(languageTag: String): Context {
    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration).apply {
        setLocales(LocaleList(locale))
    }
    return createConfigurationContext(configuration)
}
