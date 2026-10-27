package vpn.moonlight.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import vpn.moonlight.design.ml

/**
 * The page everything sits on. It carries the colour, as the cabinet's does, and
 * two soft glows of the pale yellow behind it — what the translucent panes on
 * top read as frosted glass against. Drawn once, under every screen.
 */
@Composable
fun MlPage(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val colors = ml.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.pageTop, colors.pageBottom))),
    ) {
        val glow = colors.highlight.copy(alpha = if (colors.isLight) 0.55f else 0.07f)
        Canvas(Modifier.fillMaxSize()) {
            fun halo(center: Offset, radius: Float) = drawCircle(
                Brush.radialGradient(listOf(glow, Color.Transparent), center, radius),
                radius,
                center,
            )
            halo(Offset(size.width * 0.95f, size.height * 0.08f), size.width * 0.75f)
            halo(Offset(size.width * 0.05f, size.height * 0.62f), size.width * 0.65f)
        }
        content()
    }
}
