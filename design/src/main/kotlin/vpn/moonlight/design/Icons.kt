package vpn.moonlight.design

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lucide 0.468.0 — the icon set the design is drawn with — carried across as raw
 * SVG path data rather than redrawn or swapped for Material equivalents, so the
 * stroke geometry is identical. Non-path elements (rect/circle/line/polyline)
 * were converted to equivalent path commands at generation time.
 *
 * Generated. Do not hand-edit; see scripts/README for regeneration.
 */
@Immutable
data class MlIconSpec(val paths: List<String>)

private fun MlIconSpec.toImageVector(strokeWidth: Float): ImageVector =
    ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = strokeWidth,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

/**
 * Icons are stroked, so [tint] recolours the whole vector. [strokeWidth] follows
 * the design, which thickens a few glyphs (chevrons 2.2, chips 2.4, the success
 * check 2.6) above lucide's default 2.
 */
@Composable
fun MlIcon(
    spec: MlIconSpec,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    tint: Color = Color.Unspecified,
    strokeWidth: Float = 2f,
) {
    val vector = remember(spec, strokeWidth) { spec.toImageVector(strokeWidth) }
    Icon(
        imageVector = vector,
        contentDescription = null,
        modifier = modifier.size(size),
        tint = if (tint == Color.Unspecified) ml.colors.text else tint,
    )
}

object MlIcons {
    val Activity = MlIconSpec(listOf("M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2"))
    val Camera = MlIconSpec(
        listOf(
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M9,13 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0 z",
        ),
    )
    val Check = MlIconSpec(listOf("M20 6 9 17l-5-5"))
    val ChevronDown = MlIconSpec(listOf("m6 9 6 6 6-6"))
    val ChevronLeft = MlIconSpec(listOf("m15 18-6-6 6-6"))
    val ChevronRight = MlIconSpec(listOf("m9 18 6-6-6-6"))
    val Copy = MlIconSpec(
        listOf(
        "M10,8 h10 a2,2 0 0 1 2,2 v10 a2,2 0 0 1 -2,2 h-10 a2,2 0 0 1 -2,-2 v-10 a2,2 0 0 1 2,-2 z",
        "M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2",
        ),
    )
    val ExternalLink = MlIconSpec(
        listOf(
        "M15 3h6v6",
        "M10 14 21 3",
        "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6",
        ),
    )
    val Globe = MlIconSpec(
        listOf(
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0 z",
        "M12 2a14.5 14.5 0 0 0 0 20 14.5 14.5 0 0 0 0-20",
        "M2 12h20",
        ),
    )
    val Headphones = MlIconSpec(listOf("M3 14h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-7a9 9 0 0 1 18 0v7a2 2 0 0 1-2 2h-1a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2h3"))
    val Info = MlIconSpec(
        listOf(
        "M2,12 a10,10 0 1 0 20,0 a10,10 0 1 0 -20,0 z",
        "M12 16v-4",
        "M12 8h.01",
        ),
    )
    val Layers = MlIconSpec(
        listOf(
        "M12.83 2.18a2 2 0 0 0-1.66 0L2.6 6.08a1 1 0 0 0 0 1.83l8.58 3.91a2 2 0 0 0 1.66 0l8.58-3.9a1 1 0 0 0 0-1.83z",
        "M2 12a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 12",
        "M2 17a1 1 0 0 0 .58.91l8.6 3.91a2 2 0 0 0 1.65 0l8.58-3.9A1 1 0 0 0 22 17",
        ),
    )
    val Link2 = MlIconSpec(
        listOf(
        "M9 17H7A5 5 0 0 1 7 7h2",
        "M15 7h2a5 5 0 1 1 0 10h-2",
        "M8,12 L16,12",
        ),
    )
    val Lock = MlIconSpec(
        listOf(
        "M5,11 h14 a2,2 0 0 1 2,2 v7 a2,2 0 0 1 -2,2 h-14 a2,2 0 0 1 -2,-2 v-7 a2,2 0 0 1 2,-2 z",
        "M7 11V7a5 5 0 0 1 10 0v4",
        ),
    )
    val MessageCircle = MlIconSpec(listOf("M7.9 20A9 9 0 1 0 4 16.1L2 22Z"))
    val Plus = MlIconSpec(
        listOf(
        "M5 12h14",
        "M12 5v14",
        ),
    )
    val Power = MlIconSpec(
        listOf(
        "M12 2v10",
        "M18.4 6.6a9 9 0 1 1-12.77.04",
        ),
    )
    val QrCode = MlIconSpec(
        listOf(
        "M4,3 h3 a1,1 0 0 1 1,1 v3 a1,1 0 0 1 -1,1 h-3 a1,1 0 0 1 -1,-1 v-3 a1,1 0 0 1 1,-1 z",
        "M17,3 h3 a1,1 0 0 1 1,1 v3 a1,1 0 0 1 -1,1 h-3 a1,1 0 0 1 -1,-1 v-3 a1,1 0 0 1 1,-1 z",
        "M4,16 h3 a1,1 0 0 1 1,1 v3 a1,1 0 0 1 -1,1 h-3 a1,1 0 0 1 -1,-1 v-3 a1,1 0 0 1 1,-1 z",
        "M21 16h-3a2 2 0 0 0-2 2v3",
        "M21 21v.01",
        "M12 7v3a2 2 0 0 1-2 2H7",
        "M3 12h.01",
        "M12 3h.01",
        "M12 16v.01",
        "M16 12h1",
        "M21 12v.01",
        "M12 21v-1",
        ),
    )
    val RefreshCw = MlIconSpec(
        listOf(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
        ),
    )
    val Send = MlIconSpec(
        listOf(
        "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11z",
        "m21.854 2.147-10.94 10.939",
        ),
    )
    val Settings = MlIconSpec(
        listOf(
        "M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z",
        "M9,12 a3,3 0 1 0 6,0 a3,3 0 1 0 -6,0 z",
        ),
    )
    val ShieldCheck = MlIconSpec(
        listOf(
        "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z",
        "m9 12 2 2 4-4",
        ),
    )
    val Smartphone = MlIconSpec(
        listOf(
        "M7,2 h10 a2,2 0 0 1 2,2 v16 a2,2 0 0 1 -2,2 h-10 a2,2 0 0 1 -2,-2 v-16 a2,2 0 0 1 2,-2 z",
        "M12 18h.01",
        ),
    )
    val Sparkles = MlIconSpec(
        listOf(
        "M9.937 15.5A2 2 0 0 0 8.5 14.063l-6.135-1.582a.5.5 0 0 1 0-.962L8.5 9.936A2 2 0 0 0 9.937 8.5l1.582-6.135a.5.5 0 0 1 .963 0L14.063 8.5A2 2 0 0 0 15.5 9.937l6.135 1.581a.5.5 0 0 1 0 .964L15.5 14.063a2 2 0 0 0-1.437 1.437l-1.582 6.135a.5.5 0 0 1-.963 0z",
        "M20 3v4",
        "M22 5h-4",
        "M4 17v2",
        "M5 18H3",
        ),
    )
    val TriangleAlert = MlIconSpec(
        listOf(
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3",
        "M12 9v4",
        "M12 17h.01",
        ),
    )
    val WifiOff = MlIconSpec(
        listOf(
        "M12 20h.01",
        "M8.5 16.429a5 5 0 0 1 7 0",
        "M5 12.859a10 10 0 0 1 5.17-2.69",
        "M19 12.859a10 10 0 0 0-2.007-1.523",
        "M2 8.82a15 15 0 0 1 4.177-2.643",
        "M22 8.82a15 15 0 0 0-11.288-3.764",
        "m2 2 20 20",
        ),
    )
    val X = MlIconSpec(
        listOf(
        "M18 6 6 18",
        "m6 6 12 12",
        ),
    )
    val Zap = MlIconSpec(listOf("M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"))
    val Trash2 = MlIconSpec(
        listOf(
        "M3 6h18",
        "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6",
        "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2",
        "M10,11 L10,17",
        "M14,11 L14,17",
        ),
    )
    val Search = MlIconSpec(
        listOf(
        "M3,11 a8,8 0 1 0 16,0 a8,8 0 1 0 -16,0 z",
        "m21 21-4.3-4.3",
        ),
    )
}
