package vpn.moonlight.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.ml
import vpn.moonlight.design.pressScale
import vpn.moonlight.design.rememberPressSource

/** The full-width accent CTA: 52dp tall, pill, ink-on-accent, weight 800. */
@Composable
fun MlPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: MlIconSpec? = null,
) {
    val source = rememberPressSource()
    Row(
        modifier
            .fillMaxWidth()
            .height(52.dp)
            .pressScale(source, MlMotion.PressButton, enabled)
            .clip(MlShape.Pill)
            .background(if (enabled) ml.colors.accent else ml.colors.surface3)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ink = if (enabled) ml.colors.textOnAccent else ml.colors.textMuted
        if (leadingIcon != null) {
            MlIcon(leadingIcon, size = 18.dp, tint = ink)
            Box(Modifier.size(9.dp))
        }
        MlText(label, ml.type.bodyEmphatic, color = ink)
    }
}

/**
 * A circular icon button, as used for back, ping and refresh.
 *
 * @param label what it does, for a screen reader — the button has no text of
 *  its own to be read.
 */
@Composable
fun MlIconButton(
    icon: MlIconSpec,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
    iconSize: Dp = 20.dp,
    strokeWidth: Float = 2.2f,
    background: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    enabled: Boolean = true,
    label: String? = null,
    content: @Composable (() -> Unit)? = null,
) {
    val source = rememberPressSource()
    Box(
        modifier
            .size(size)
            .pressScale(source, MlMotion.PressIcon, enabled)
            .clip(MlShape.Pill)
            .background(if (background == Color.Unspecified) ml.colors.surface2 else background)
            .clickable(
                interactionSource = source,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            )
            .then(
                if (label != null) Modifier.semantics { contentDescription = label } else Modifier,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (content != null) {
            content()
        } else {
            MlIcon(
                icon,
                size = iconSize,
                tint = if (tint == Color.Unspecified) ml.colors.text2 else tint,
                strokeWidth = strokeWidth,
            )
        }
    }
}

/**
 * A pill-shaped button carrying a label and optional leading slot — the refresh
 * control in the connect header, and the quick-pick chips under the dial.
 */
@Composable
fun MlPillButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp,
    background: Color = Color.Unspecified,
    contentColor: Color = Color.Unspecified,
    bordered: Boolean = false,
    horizontalPadding: Dp = 14.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val source = rememberPressSource()
    val bg by animateColorAsState(
        if (background == Color.Unspecified) ml.colors.surface else background,
        MlMotion.paint(),
        label = "pillBg",
    )
    val fg by animateColorAsState(
        if (contentColor == Color.Unspecified) ml.colors.text2 else contentColor,
        MlMotion.paint(),
        label = "pillFg",
    )
    Row(
        modifier
            .height(height)
            .pressScale(source, MlMotion.PressIcon)
            .clip(MlShape.Pill)
            .background(bg)
            .then(if (bordered) Modifier.border(1.dp, ml.colors.hairline, MlShape.Pill) else Modifier)
            .clickable(source, indication = null, onClick = onClick)
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides fg,
        ) {
            content()
        }
    }
}

/**
 * The quiet partner of [MlPrimaryButton]: a glass pill with an ink label, for
 * the second action beside a main one ("Продлить" next to "Подключить").
 */
@Composable
fun MlSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: MlIconSpec? = null,
    contentColor: Color = Color.Unspecified,
) {
    val source = rememberPressSource()
    val ink = when {
        !enabled -> ml.colors.textMuted
        contentColor != Color.Unspecified -> contentColor
        else -> ml.colors.text
    }
    Row(
        modifier
            .height(52.dp)
            .pressScale(source, MlMotion.PressButton, enabled)
            .clip(MlShape.Pill)
            .background(ml.colors.surface)
            .border(1.dp, ml.colors.hairline, MlShape.Pill)
            .clickable(source, indication = null, enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            MlIcon(leadingIcon, size = 18.dp, tint = ink)
            Box(Modifier.size(9.dp))
        }
        MlText(label, ml.type.bodyEmphatic, color = ink, maxLines = 1)
    }
}

/** The cabinet's round yellow plus beside the tab bar: the way to buy. */
@Composable
fun MlFab(
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: MlIconSpec = vpn.moonlight.design.MlIcons.Plus,
) {
    MlIconButton(
        icon = icon,
        onClick = onClick,
        modifier = modifier,
        size = 64.dp,
        iconSize = 26.dp,
        strokeWidth = 2.4f,
        background = ml.colors.highlight,
        tint = ml.colors.onHighlight,
        label = label,
    )
}
