package vpn.moonlight.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.ml
import vpn.moonlight.design.pressScale
import vpn.moonlight.design.rememberPressSource

/**
 * The connect control is the moon from the logo.
 *
 * Disconnected it is the logo's crescent, dim, with its two stars; connected the
 * shadow slides off and it is a full moon, lit in the logo's own colour in both
 * themes, and the stars fade as the sky brightens. Changing state is the moon
 * changing phase — the one moment in the interface allowed to be expressive,
 * and the brand doing the explaining. While the tunnel is changing state a thin
 * orbit turns round it.
 *
 * It replaces a dial whose halo breathed for as long as the screen was up,
 * connected or not: an animation that redrew sixty times a second to say
 * nothing was happening. Here nothing moves unless something is changing. The
 * phase is read where it is drawn, so even while it changes it repaints one
 * canvas and recomposes nothing.
 */
@Composable
fun MoonButton(
    connected: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    side: Dp = MoonButtonDefaults.Large,
    enabled: Boolean = true,
    onClickLabel: String? = null,
) {
    val source = rememberPressSource()
    val colors = ml.colors

    // 0 is the crescent, 1 the full moon. Not delegated: it is read only inside
    // the draw block below.
    val phase = animateFloatAsState(
        targetValue = if (connected) 1f else 0f,
        animationSpec = MlMotion.standard(0.001f),
        label = "moonPhase",
    )

    // Reused across frames; a phase change would otherwise allocate three paths
    // a frame for as long as it runs.
    val disc = remember { Path() }
    val shadow = remember { Path() }
    val lit = remember { Path() }

    Box(
        modifier
            // requiredSize, not size: `size` is coerced into the parent's
            // constraints, so on a short screen the button would be measured
            // wider than it is tall — a visible ellipse.
            .requiredSize(side)
            .alpha(if (enabled) 1f else 0.5f)
            .pressScale(source, MlMotion.PressButton, enabled)
            .clip(CircleShape)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = onClickLabel,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            val full = phase.value
            val radius = size.minDimension / 2f
            val hairline = 1.dp.toPx()

            // The surface the moon sits on, and its rim: a quiet line, turning
            // the moon's own colour as it lights.
            drawCircle(colors.surface, radius)
            drawCircle(
                color = lerp(colors.hairline, colors.highlightFill.copy(alpha = 0.75f), full),
                radius = radius - hairline / 2f,
                style = Stroke(hairline),
            )

            val moon = size.minDimension * 0.5f
            val moonRadius = moon / 2f

            // Moonlight, once there is some.
            if (full > 0.01f) {
                val reach = moonRadius + moon * 0.6f
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to colors.highlightFill.copy(alpha = 0.34f * full),
                        moonRadius / reach to colors.highlightFill.copy(alpha = 0.22f * full),
                        1f to Color.Transparent,
                        center = center,
                        radius = reach,
                    ),
                    radius = reach,
                )
            }

            // A lit disc with a second disc cut out of it. The cut's offset is
            // the phase: close in, a crescent lit to the lower left as the logo
            // draws it; slid off, a full moon. It is cut rather than painted
            // over, so the surface shows through the dark part as it does
            // round it.
            disc.rewind()
            disc.addOval(Rect(center, moonRadius))
            shadow.rewind()
            shadow.addOval(
                Rect(
                    center + Offset(moon * lerp(0.3f, 1.15f, full), -moon * lerp(0.24f, 0.9f, full)),
                    moonRadius,
                ),
            )
            lit.rewind()
            lit.op(disc, shadow, PathOperation.Difference)
            drawPath(lit, lerp(colors.text2, colors.highlightFill, full))

            // The logo's two stars, up and to the right of the moon. They
            // belong to the night, so they go as the moon fills.
            if (full < 0.99f) {
                val starlight = colors.text2.copy(alpha = colors.text2.alpha * (1f - full))
                drawCircle(starlight, moon * 0.05f, center + Offset(moon * 0.5f, -moon * 0.5f))
                drawCircle(starlight, moon * 0.0325f, center + Offset(moon * 0.24f, -moon * 0.72f))
            }
        }

        AnimatedVisibility(
            visible = busy,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(MlMotion.paint()),
            exit = fadeOut(MlMotion.paint()),
        ) {
            Orbit(color = colors.text, modifier = Modifier.padding(side * 0.06f))
        }
    }
}

/**
 * A thin arc circling the moon while the tunnel connects or disconnects.
 *
 * Composed only while it is needed, so the repeating animation behind it exists
 * only then; and the angle is read in the draw block, so a turn is a repaint.
 */
@Composable
private fun Orbit(color: Color, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "orbit")
    val turn = transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "orbitTurn",
    )
    Canvas(modifier) {
        val stroke = 1.5.dp.toPx()
        rotate(turn.value) {
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * 0.22f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

object MoonButtonDefaults {
    /** While it is the only thing on the page. */
    val Large = 192.dp

    /** With the server list open under it. */
    val Compact = 96.dp
}

/** The paired stat block under the button: two centred values with a hairline between. */
@Composable
fun MlStatPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    modifier: Modifier = Modifier,
) {
    MlCard(modifier, shape = RoundedCornerShape(18.dp)) {
        Row(
            Modifier.height(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell(leftLabel, leftValue, Modifier.weight(1f))
            MlVerticalRule()
            StatCell(rightLabel, rightValue, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        MlText(label.uppercase(), ml.type.overline, color = ml.colors.textMuted)
        MlText(value, ml.type.lead.copy(fontSize = 19.sp), maxLines = 1)
    }
}
