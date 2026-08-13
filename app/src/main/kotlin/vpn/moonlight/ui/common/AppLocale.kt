package vpn.moonlight.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import vpn.moonlight.data.model.AppLanguage
import vpn.moonlight.data.util.localizedFor

/**
 * Renders [content] in the chosen language without restarting anything.
 *
 * `stringResource` reads its text from `LocalContext`'s resources and uses
 * `LocalConfiguration` to know when to invalidate, so overriding both is enough
 * to re-resolve every string on the next recomposition.
 *
 * Note that the provided context is a configuration context, not the Activity.
 * Anything that needs to launch an activity from it must set
 * `FLAG_ACTIVITY_NEW_TASK`.
 */
@Composable
fun WithAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized = remember(language, configuration) {
        context.localizedFor(language.tag)
    }

    CompositionLocalProvider(
        LocalContext provides localized,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
