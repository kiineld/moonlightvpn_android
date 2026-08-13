package vpn.moonlight.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.ml
import vpn.moonlight.design.rememberPressSource

/**
 * The 44×26 toggle. The knob slides on the overshoot curve; the track paints on
 * the calm one, exactly as in the design.
 */
@Composable
fun MlSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val track by animateColorAsState(
        if (checked) ml.colors.accent else ml.colors.surface3,
        MlMotion.paint(),
        label = "switchTrack",
    )
    val knobOffset by animateDpAsState(
        if (checked) 18.dp else 0.dp,
        MlMotion.slide(),
        label = "switchKnob",
    )
    Box(
        modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(MlShape.Pill)
            .background(track)
            .clickable(
                interactionSource = rememberPressSource(),
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            Modifier
                .padding(3.dp)
                .offset(x = knobOffset)
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

/**
 * A segmented pill. The selected segment is an accent fill; unselected segments
 * are transparent with muted labels.
 */
@Composable
fun MlSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmentHeight: Dp = 34.dp,
    fillWidth: Boolean = true,
) {
    Row(
        modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .clip(MlShape.Pill)
            .background(ml.colors.surface2)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                if (selected) ml.colors.accent else Color.Transparent,
                MlMotion.paint(),
                label = "segBg",
            )
            val fg by animateColorAsState(
                if (selected) ml.colors.textOnAccent else ml.colors.textMuted,
                MlMotion.paint(),
                label = "segFg",
            )
            Box(
                Modifier
                    .then(if (fillWidth) Modifier.weight(1f) else Modifier)
                    .height(segmentHeight)
                    .clip(MlShape.Pill)
                    .background(bg)
                    .clickable(
                        interactionSource = rememberPressSource(),
                        indication = null,
                        onClick = { onSelect(index) },
                    )
                    .then(if (fillWidth) Modifier else Modifier.padding(horizontal = 12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                MlText(label, ml.type.metaEmphatic, color = fg)
            }
        }
    }
}

/** The 8dp traffic / quota bar. */
@Composable
fun MlProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp,
    color: Color = Color.Unspecified,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MlShape.Pill)
            .background(ml.colors.surface3),
    ) {
        val safe = fraction.coerceIn(0f, 1f)
        if (safe > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(safe)
                    .clip(MlShape.Pill)
                    .background(if (color == Color.Unspecified) ml.colors.accent else color),
            )
        }
    }
}

/** A vertical hairline, as between the two stats under the dial. */
@Composable
fun MlVerticalRule(height: Dp = 34.dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .width(1.dp)
            .height(height)
            .background(ml.colors.hairline),
    )
}
