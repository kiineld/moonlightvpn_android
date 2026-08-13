package vpn.moonlight.design.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlIcon
import vpn.moonlight.design.MlIconSpec
import vpn.moonlight.design.MlIcons
import vpn.moonlight.design.ml

/**
 * The workhorse list row: a category-filled icon tile, a title with an optional
 * sub-line, and a trailing affordance. Used across settings, subscription and
 * import.
 */
@Composable
fun MlNavRow(
    title: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    tileFill: Color? = null,
    icon: MlIconSpec? = null,
    titleEmphatic: Boolean = false,
    trailing: (@Composable RowScope.() -> Unit)? = { MlChevron() },
    leading: (@Composable () -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when {
                leading != null -> leading()
                icon != null && tileFill != null -> MlIconTile(tileFill) {
                    MlIcon(icon, size = 19.dp, tint = ml.colors.textOnAccent)
                }
            }
            Column(Modifier.weight(1f)) {
                MlText(
                    title,
                    if (titleEmphatic) ml.type.bodyEmphatic else ml.type.bodyTitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Box(Modifier.padding(top = 2.dp)) {
                        MlText(
                            subtitle,
                            ml.type.metaSm,
                            color = ml.colors.textMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            trailing?.invoke(this)
        }
    }

    if (onClick == null) {
        Box(modifier.fillMaxWidth()) { content() }
    } else {
        MlPressable(onClick, modifier.fillMaxWidth()) { content() }
    }
}

@Composable
fun MlChevron(modifier: Modifier = Modifier) {
    MlIcon(
        MlIcons.ChevronRight,
        modifier,
        size = 18.dp,
        tint = ml.colors.textMuted,
        strokeWidth = 2.2f,
    )
}

@Composable
fun MlExternalMark(modifier: Modifier = Modifier) {
    MlIcon(MlIcons.ExternalLink, modifier, size = 17.dp, tint = ml.colors.textMuted)
}
