package vpn.moonlight.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.ml
import vpn.moonlight.design.pressScale
import vpn.moonlight.design.rememberPressSource

/**
 * The 244dp connect dial: a hairline track, an accent sweep for throughput, a
 * breathing halo while connected, and a three-line centre stack.
 *
 * [progress] is the sweep fraction. [active] drives the halo and the accent
 * tone, so a connecting state can show tone without a full sweep.
 */
@Composable
fun ConnectDial(
    statusLabel: String,
    bigLabel: String,
    timer: String,
    progress: Float,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = DefaultDiameter,
) {
    // Everything inside scales with the dial, so a smaller dial is a smaller dial
    // rather than a full-size label crammed into a smaller ring.
    val ratio = (diameter / DefaultDiameter).coerceIn(0.4f, 1f)
    val source = rememberPressSource()
    val tone by animateColorAsState(
        if (active) ml.colors.accentInk else ml.colors.textMuted,
        tween(400, easing = MlMotion.Ease),
        label = "dialTone",
    )
    val sweep by androidx.compose.animation.core.animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(600, easing = MlMotion.Ease),
        label = "dialSweep",
    )
    val haloAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 0.5f else 0f,
        animationSpec = tween(500, easing = MlMotion.Ease),
        label = "haloAlpha",
    )

    // The halo breathes: it grows slightly while fading out, then returns.
    val breathe = rememberInfiniteTransition(label = "breathe")
    val breatheScale by breathe.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = MlMotion.Ease),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheScale",
    )
    val breatheAlpha by breathe.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(2100, easing = MlMotion.Ease),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breatheAlpha",
    )

    val trackColor = ml.colors.hairline
    val accent = ml.colors.accent

    Box(
        modifier
            // requiredSize, not size: `size` is coerced into the parent's
            // constraints, so when the node list opened and the stage got shorter
            // the dial was measured 244dp wide by whatever height was left — a
            // visible ellipse. requiredSize keeps it circular regardless.
            .requiredSize(diameter)
            .pressScale(source, 0.975f)
            .clip(CircleShape)
            .clickable(source, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Halo, drawn outside the dial's own bounds.
        Box(
            Modifier
                .matchParentSize()
                .scale(breatheScale)
                .drawBehind {
                    if (haloAlpha > 0f) {
                        val inset = 10.dp.toPx() * ratio
                        drawCircle(
                            color = accent.copy(alpha = haloAlpha * breatheAlpha / 0.32f),
                            radius = size.minDimension / 2f + inset,
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                },
        )

        Canvas(Modifier.matchParentSize()) {
            val ringWidth = 2.dp.toPx()
            val sweepWidth = 6.dp.toPx() * ratio
            val radius = size.minDimension / 2f

            drawCircle(
                color = trackColor,
                radius = radius - ringWidth / 2f,
                style = Stroke(width = ringWidth),
            )

            if (sweep > 0f) {
                val inset = sweepWidth / 2f
                drawArc(
                    color = accent,
                    startAngle = -90f,
                    sweepAngle = 360f * sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - sweepWidth, size.height - sweepWidth),
                    style = Stroke(width = sweepWidth, cap = StrokeCap.Butt),
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp * ratio),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp * ratio),
            ) {
                Box(
                    Modifier
                        .size(8.dp * ratio)
                        .clip(CircleShape)
                        .drawBehind {
                            if (active) {
                                // Stands in for the CSS glow on the status dot.
                                drawCircle(
                                    color = accent.copy(alpha = 0.35f),
                                    radius = size.minDimension,
                                )
                            }
                            drawCircle(color = tone)
                        },
                )
                MlText(
                    statusLabel.uppercase(),
                    ml.type.overline.copy(fontSize = ml.type.overline.fontSize * ratio),
                    color = tone,
                )
            }
            MlText(
                bigLabel,
                ml.type.title.copy(fontSize = 26.sp * ratio),
                color = ml.colors.text,
                maxLines = 1,
            )
            MlText(
                timer,
                ml.type.mono.copy(fontSize = ml.type.mono.fontSize * ratio),
                color = tone,
            )
        }
    }
}

/** The design's full-size dial; smaller values are used when space is tight. */
val DefaultDiameter = 244.dp

/** The paired stat block under the dial: two centred values with a hairline between. */
@Composable
fun MlStatPair(
    leftLabel: String,
    leftValue: String,
    rightLabel: String,
    rightValue: String,
    modifier: Modifier = Modifier,
) {
    MlCard(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) {
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
        MlText(value, ml.type.lead.copy(fontSize = 19.sp))
    }
}
