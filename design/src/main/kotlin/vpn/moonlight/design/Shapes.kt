package vpn.moonlight.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.dp

/** Mirrors `tokens/radii.css`. `Pill` stands in for the CSS `999px`. */
@Immutable
object MlShape {
    val Pill = RoundedCornerShape(percent = 50)
    val HeroCard = RoundedCornerShape(26.dp)
    val Card = RoundedCornerShape(22.dp)
    val Scanner = RoundedCornerShape(24.dp)
    val LogoTile = RoundedCornerShape(20.dp)
    val Input = RoundedCornerShape(16.dp)
    val Row = RoundedCornerShape(16.dp)
    val IconTile = RoundedCornerShape(13.dp)
    val IconTileLg = RoundedCornerShape(14.dp)
    val LogoSm = RoundedCornerShape(11.dp)
}
