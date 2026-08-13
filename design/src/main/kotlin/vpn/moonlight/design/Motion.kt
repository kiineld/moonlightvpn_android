package vpn.moonlight.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween

/**
 * Motion is short, eased, and mostly about position. Two curves do almost all
 * the work: a calm ease for colour and opacity, and an overshoot curve for
 * anything that slides into place — the tab pill, segmented pills, toggle knobs.
 * Presses shrink; nothing scales up.
 */
object MlMotion {
    val Ease: Easing = CubicBezierEasing(0.2f, 0.7f, 0.3f, 1f)
    val EaseBounce: Easing = CubicBezierEasing(0.5f, 1.4f, 0.4f, 1f)
    val EaseSlide: Easing = CubicBezierEasing(0.5f, 1.28f, 0.32f, 1f)
    val EaseInOut: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
    val EaseRise: Easing = CubicBezierEasing(0.22f, 0.85f, 0.3f, 1f)

    const val DurPress = 180
    const val DurPaint = 200
    const val DurSlide = 420
    const val DurEnter = 350
    const val DurRise = 520

    /** Press scales — the whole system uses exactly these three. */
    const val PressCard = 0.985f
    const val PressButton = 0.97f
    const val PressIcon = 0.92f

    fun <T> press(): FiniteAnimationSpec<T> = tween(DurPress, easing = Ease)
    fun <T> paint(): FiniteAnimationSpec<T> = tween(DurPaint, easing = Ease)
    /** Sliding pills and knobs: small elements where the overshoot reads as life. */
    fun <T> slide(): FiniteAnimationSpec<T> = tween(DurSlide, easing = EaseSlide)

    /**
     * Layout changes — anything that resizes a large element or collapses a
     * section.
     *
     * Deliberately not [slide]. `EaseSlide` is 94% done in 168ms of its 420ms and
     * overshoots to 101.7% before settling, so a resize appears to jump and then
     * twitch. `EaseInOut` spreads the change evenly across the whole duration,
     * and every element in one transition must share this spec or they arrive at
     * different times and the result looks broken even when each is smooth.
     */
    fun <T> layout(): FiniteAnimationSpec<T> = tween(DurSlide, easing = EaseInOut)
    fun <T> enter(): FiniteAnimationSpec<T> = tween(DurEnter, easing = Ease)
}
