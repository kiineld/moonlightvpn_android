package vpn.moonlight.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalMoonlightColors = staticCompositionLocalOf { MoonlightDarkColors }
private val LocalMoonlightTypography = staticCompositionLocalOf { MoonlightTypography() }

object MoonlightTheme {
    val colors: MoonlightColors
        @Composable @ReadOnlyComposable get() = LocalMoonlightColors.current

    val type: MoonlightTypography
        @Composable @ReadOnlyComposable get() = LocalMoonlightTypography.current
}

/** Shorter alias — this is referenced on nearly every line of UI code. */
val ml: MoonlightTheme get() = MoonlightTheme

@Composable
fun MoonlightTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MoonlightDarkColors else MoonlightLightColors
    val typography = MoonlightTypography()

    CompositionLocalProvider(
        LocalMoonlightColors provides colors,
        LocalMoonlightTypography provides typography,
    ) {
        MaterialTheme(colorScheme = colors.toMaterialScheme()) {
            ProvideTextStyle(typography.body.copy(color = colors.text), content)
        }
    }
}

/**
 * Material3 is present only so stock components have a sane fallback; the
 * design system itself never draws from it.
 */
private fun MoonlightColors.toMaterialScheme() = if (isLight) {
    lightColorScheme(
        primary = accent,
        onPrimary = textOnAccent,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        error = danger,
    )
} else {
    darkColorScheme(
        primary = accent,
        onPrimary = textOnAccent,
        background = bg,
        onBackground = text,
        surface = surface,
        onSurface = text,
        error = danger,
    )
}
