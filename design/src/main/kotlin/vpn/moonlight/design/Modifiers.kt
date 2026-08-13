package vpn.moonlight.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * The system's only press feedback: the target shrinks. Use the scale that
 * matches the element — [MlMotion.PressCard] for large rows and cards,
 * [MlMotion.PressButton] for buttons, [MlMotion.PressIcon] for icon buttons.
 */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressed: Float = MlMotion.PressButton,
    enabled: Boolean = true,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) pressed else 1f,
        animationSpec = MlMotion.press(),
        label = "pressScale",
    )
    return this.scale(scale)
}
