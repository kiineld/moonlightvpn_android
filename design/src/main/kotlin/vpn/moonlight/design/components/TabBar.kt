package vpn.moonlight.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.ml
import vpn.moonlight.design.rememberPressSource

/** One destination in the floating tab bar. */
data class MlTab(val icon: MlIconSpec, val contentDescription: String)

/**
 * The floating pill navigation. An accent puck slides behind the active icon on
 * the overshoot curve; the icons themselves only change colour.
 */
@Composable
fun MlTabBar(
    tabs: List<MlTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemSize = 52.dp
    val gap = 4.dp
    val puckOffset by animateDpAsState(
        targetValue = (itemSize + gap) * selectedIndex.coerceAtLeast(0),
        animationSpec = MlMotion.slide(),
        label = "tabPuck",
    )

    Box(
        modifier
            .clip(MlShape.Pill)
            .background(ml.colors.surfaceNav)
            .border(1.dp, ml.colors.hairline, MlShape.Pill)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .offset(x = puckOffset)
                .size(itemSize)
                .clip(MlShape.Pill)
                .background(ml.colors.accent),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
            tabs.forEachIndexed { index, tab ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) ml.colors.textOnAccent else ml.colors.textMuted,
                    MlMotion.paint(),
                    label = "tabTint",
                )
                Box(
                    Modifier
                        .size(itemSize)
                        .clip(MlShape.Pill)
                        .clickable(
                            interactionSource = rememberPressSource(),
                            indication = null,
                            role = Role.Tab,
                            onClickLabel = tab.contentDescription,
                            onClick = { onSelect(index) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MlIcon(tab.icon, size = 22.dp, tint = tint)
                }
            }
        }
    }
}
