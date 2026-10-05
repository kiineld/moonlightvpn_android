package vpn.moonlight.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Moonlight motion: one curve.
 *
 * Everything that moves — a page arriving, the server list opening, the moon
 * growing, a pill gliding, a switch — moves on [standard]: a spring damped just
 * short of settling on its own, so it eases in and lands without the overshoot
 * that makes a bounce read as a toy. Only changes with no movement in them (a
 * colour, a press) use [paint]. The app used to carry five curves, two of them
 * overshooting, and screens felt like different apps as a result.
 */
object MlMotion {
    /**
     * The Mac's `spring(response: 0.5, dampingFraction: 0.9)`. A spring with a
     * response of `r` seconds has a stiffness of `(2π / r)²`.
     */
    private const val Stiffness = 158f
    private const val Damping = 0.9f

    /** Colour, opacity and press — changes with nothing moving. */
    val EaseOut: Easing = CubicBezierEasing(0f, 0f, 0.58f, 1f)
    const val DurPaint = 180

    /** How far a page travels as it arrives. Short: it settles rather than flies in. */
    val RiseDistance = 8.dp

    /** Press scales — barely there, so a press reads as a press and not a pop. */
    const val PressCard = 0.99f
    const val PressButton = 0.975f
    const val PressIcon = 0.94f

    /**
     * The one curve for anything that moves.
     *
     * @param visibilityThreshold how close counts as arrived. Worth passing for
     *  sizes and offsets: without it a spring keeps producing frames for changes
     *  far smaller than a pixel.
     */
    fun <T> standard(visibilityThreshold: T? = null): FiniteAnimationSpec<T> =
        spring(dampingRatio = Damping, stiffness = Stiffness, visibilityThreshold = visibilityThreshold)

    fun <T> paint(): FiniteAnimationSpec<T> = tween(DurPaint, easing = EaseOut)

    // The names screens were written against. All of the moving ones are the one curve.
    fun <T> press(): FiniteAnimationSpec<T> = paint()
    fun <T> slide(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = standard(visibilityThreshold)
    fun <T> layout(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = standard(visibilityThreshold)
    fun <T> enter(visibilityThreshold: T? = null): FiniteAnimationSpec<T> = standard(visibilityThreshold)
}

/**
 * Turns a glyph while something is in flight.
 *
 * The animation exists only while [active]. A repeating animation that is merely
 * *ignored* when idle — which is what every spinner here used to be — still
 * wakes the main thread on every frame for as long as its screen is up.
 *
 * It is applied in the layer, so a turn redraws the glyph and recomposes nothing.
 */
@Composable
fun Modifier.spinWhile(active: Boolean, periodMillis: Int = 900): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "spin")
    val angle = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = LinearEasing), RepeatMode.Restart),
        label = "spinAngle",
    )
    return graphicsLayer { rotationZ = angle.value }
}

/** Pulses a glyph while something is in flight. See [spinWhile]. */
@Composable
fun Modifier.pulseWhile(active: Boolean, periodMillis: Int = 900): Modifier {
    if (!active) return this
    val transition = rememberInfiniteTransition(label = "pulse")
    val alpha = transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(periodMillis, easing = MlMotion.EaseOut), RepeatMode.Reverse),
        label = "pulseAlpha",
    )
    return graphicsLayer { this.alpha = alpha.value }
}
