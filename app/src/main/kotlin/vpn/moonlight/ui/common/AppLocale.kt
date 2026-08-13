package vpn.moonlight.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import vpn.moonlight.data.model.AppLanguage
import vpn.moonlight.data.util.localizedFor

/**
 * Renders [content] in the chosen language without restarting anything.
 *
 * `stringResource` resolves its text through `LocalResources`, so swapping that
 * one local re-resolves every string on the next recomposition.
 *
 * `LocalContext` is deliberately left alone. It must keep pointing at the
 * Activity: `rememberLauncherForActivityResult` and friends walk up from it to
 * find the `ActivityResultRegistryOwner`, and a configuration context is not in
 * that chain, so providing one made the first composition throw.
 */
@Composable
fun WithAppLanguage(language: AppLanguage, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    val localized = remember(language, configuration) {
        context.localizedFor(language.tag)
    }

    CompositionLocalProvider(
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
