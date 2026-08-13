package vpn.moonlight.design.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import vpn.moonlight.design.MlShape

private val TileLime = Color(0xFFD2FF1F)
private val TileInk = Color(0xFF101828)

/**
 * The Moonlight tile mark. Its colours are fixed rather than themed: the design
 * ships it as a flat asset that stays lime in both light and dark.
 */
@Composable
fun MlLogo(modifier: Modifier = Modifier, size: Dp = 44.dp, cornerRadius: Dp = 13.dp) {
    val vector = remember(cornerRadius) { logoVector(cornerRadius.value) }
    Image(
        painter = rememberVectorPainter(vector),
        contentDescription = null,
        modifier = modifier.size(size),
    )
}

private fun logoVector(radius: Float): ImageVector =
    ImageVector.Builder(
        defaultWidth = 44.dp,
        defaultHeight = 44.dp,
        viewportWidth = 44f,
        viewportHeight = 44f,
    ).apply {
        val r = radius.coerceIn(0f, 22f)
        addPath(
            pathData = PathParser().parsePathString(
                "M$r,0 h${44 - 2 * r} a$r,$r 0 0 1 $r,$r v${44 - 2 * r} " +
                    "a$r,$r 0 0 1 ${-r},$r h${-(44 - 2 * r)} a$r,$r 0 0 1 ${-r},${-r} " +
                    "v${-(44 - 2 * r)} a$r,$r 0 0 1 $r,${-r} z",
            ).toNodes(),
            fill = SolidColor(TileLime),
        )
        addPath(
            pathData = PathParser()
                .parsePathString("M30 22a8.4 8.4 0 1 1-9.4-8.34A10 10 0 0 0 30 22Z")
                .toNodes(),
            fill = SolidColor(TileInk),
        )
        addPath(
            pathData = PathParser()
                .parsePathString("M28.8,12.5 a1.7,1.7 0 1 0 3.4,0 a1.7,1.7 0 1 0 -3.4,0 z")
                .toNodes(),
            fill = SolidColor(TileInk),
        )
        addPath(
            pathData = PathParser()
                .parsePathString("M23.9,8 a1.1,1.1 0 1 0 2.2,0 a1.1,1.1 0 1 0 -2.2,0 z")
                .toNodes(),
            fill = SolidColor(TileInk),
        )
    }.build()

/** Kept so callers can reference the tile's own corner shape. */
val MlLogoShape = MlShape.LogoTile
