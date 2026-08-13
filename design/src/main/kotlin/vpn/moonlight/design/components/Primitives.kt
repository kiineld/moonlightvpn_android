package vpn.moonlight.design.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlMotion
import vpn.moonlight.design.MlShape
import vpn.moonlight.design.ml
import vpn.moonlight.design.pressScale
import vpn.moonlight.design.rememberPressSource

/** Text that defaults to the theme colour instead of Material's. */
@Composable
fun MlText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    textAlign: TextAlign? = null,
) {
    Text(
        text = text,
        modifier = modifier,
        style = style,
        color = if (color == Color.Unspecified) ml.colors.text else color,
        maxLines = maxLines,
        overflow = overflow,
        textAlign = textAlign,
    )
}

/** An all-caps overline label, as used above every settings group. */
@Composable
fun MlOverline(text: String, modifier: Modifier = Modifier) {
    MlText(text.uppercase(), ml.type.overline, modifier, ml.colors.textMuted)
}

/** A hairline separator. [insetStart] matches the design's 72dp icon-row inset. */
@Composable
fun MlDivider(modifier: Modifier = Modifier, insetStart: Dp = 0.dp, soft: Boolean = true) {
    Box(
        modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(if (soft) ml.colors.hairlineSoft else ml.colors.hairline),
    )
}

/** A rounded card on `surface`, the default container for grouped content. */
@Composable
fun MlCard(
    modifier: Modifier = Modifier,
    shape: Shape = MlShape.Card,
    background: Color = Color.Unspecified,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .clip(shape)
            .background(if (background == Color.Unspecified) ml.colors.surface else background),
        content = content,
    )
}

/**
 * The coloured rounded square that leads most rows: a category fill with a
 * glyph in ink on top.
 */
@Composable
fun MlIconTile(
    fill: Color,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    shape: RoundedCornerShape = MlShape.IconTile,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(fill),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A tappable surface carrying the system's press-shrink feedback. */
@Composable
fun MlPressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    pressScale: Float = MlMotion.PressCard,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = rememberPressSource(),
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .pressScale(interactionSource, pressScale, enabled)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        content = content,
    )
}
