package vpn.moonlight.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.first
import vpn.moonlight.design.UnboundedFamily
import vpn.moonlight.design.ml

private val Ease = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
private val WaneEase = CubicBezierEasing(0.45f, 0f, 0.9f, 0.6f)

/** Seconds the opening takes before the splash may leave. */
private const val OPENING = 1.1f

/**
 * The cabinet's splash, frame for frame (its `index.html` and `styles/base.css`):
 * the moon waxes from new to full — the dark limb drawn back, then the lit one
 * swelling across — and the name rises under it. Once [ready] and the opening has
 * played, it fades out as the moon grows a little, and [onGone] is called.
 */
@Composable
fun MlSplash(ready: Boolean, onGone: () -> Unit, modifier: Modifier = Modifier) {
    val clock = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }
    val gone by rememberUpdatedState(onGone)

    LaunchedEffect(Unit) {
        clock.animateTo(OPENING, tween((OPENING * 1000).toInt(), easing = LinearEasing))
    }
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        snapshotFlow { clock.value }.first { it >= OPENING }
        leave.animateTo(1f, tween(500, easing = Ease))
        gone()
    }

    fun stage(start: Float, length: Float, easing: Easing = Ease) =
        easing.transform(((clock.value - start) / length).coerceIn(0f, 1f))

    val colors = ml.colors
    MlPage(modifier.alpha(1f - leave.value)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val appear = stage(0f, 0.7f)
            Canvas(
                Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        val scale = (0.86f + 0.14f * appear) * (1f + 0.12f * leave.value)
                        scaleX = scale
                        scaleY = scale
                        alpha = appear
                    },
            ) {
                val unit = size.width / 104f
                val center = Offset(52f * unit, 52f * unit)
                val unlit = lerp(colors.bg, colors.text, 0.08f)
                val lit = colors.highlightFill
                val disc = Path().apply {
                    addOval(androidx.compose.ui.geometry.Rect(center, 48f * unit))
                }
                fun limb(scaleX: Float) = Size(98f * unit * scaleX, 97f * unit)
                clipPath(disc) {
                    drawRect(unlit)
                    drawRect(lit, size = Size(52f * unit, size.height))
                    val wane = limb(1f - stage(0.18f, 0.42f, WaneEase))
                    drawOval(unlit, Offset(center.x - wane.width / 2, center.y - wane.height / 2), wane)
                    val wax = limb(stage(0.6f, 0.5f))
                    drawOval(lit, Offset(center.x - wax.width / 2, center.y - wax.height / 2), wax)
                }
            }
            val word = stage(0.5f, 0.6f)
            MlText(
                "moonlight",
                ml.type.title.copy(
                    fontFamily = UnboundedFamily,
                    fontWeight = FontWeight(600),
                    fontSize = 22.sp,
                    letterSpacing = (-0.02f).em,
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = word
                    translationY = (1f - word) * 10.dp.toPx()
                },
            )
        }
    }
}
