package vpn.moonlight.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Moonlight palette — black and white, as on the Mac.
 *
 * The interface is monochrome: a black canvas, near-black surfaces told apart by
 * a step of grey, white type, and white as the one interactive colour (black, in
 * the light theme). Colour is spent in exactly two places — the logo's lime,
 * which is the brand and appears nowhere else but on the moon it lights, and the
 * small signals that carry meaning: latency, errors, warnings.
 *
 * The token names are the ones every screen was written against; what they
 * resolve to is what changed. The accent still has three roles, because the
 * screens still ask for them separately:
 *
 *  - [accent]     fills — buttons, the plan card, active pills
 *  - [accentInk]  accent as type or a glyph on a surface
 *  - [accentLine] accent as a thin mark — bars, dots, hairlines, focus rings
 *
 * The canvas is true black. On the OLED panels most phones have, a black pixel
 * is one that is switched off.
 */
@Immutable
data class MoonlightColors(
    /** The logo's lime, and the lit moon. Nothing else. */
    val brand: Color,
    val brandInk: Color,

    val bg: Color,
    val bgDeep: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val surfaceNav: Color,

    val text: Color,
    val text2: Color,
    val textMuted: Color,
    val textOnAccent: Color,
    val textLink: Color,

    val accent: Color,
    val accentHover: Color,
    val accentQuiet: Color,
    val accentInk: Color,
    val accentInkStrong: Color,
    val accentLine: Color,

    val hairline: Color,
    val hairlineSoft: Color,
    val inkWash: Color,
    val inkWashSoft: Color,

    val danger: Color,
    val dangerQuiet: Color,
    val warning: Color,
    val info: Color,
    val heroGold: Color,

    /**
     * Fills for the tile behind a row's glyph. All one quiet grey now; the glyph
     * on it is drawn in [text], not [textOnAccent].
     */
    val cat1: Color,
    val cat2: Color,
    val cat3: Color,
    val cat4: Color,
    val cat5: Color,

    /** Latency / service severities — the only colour in the interface proper. */
    val statusUpInk: Color,
    val statusDegradedInk: Color,
    val statusMaintenanceInk: Color,
    val statusPartialInk: Color,
    val statusDownInk: Color,

    val telegram: Color,
    val isLight: Boolean,
) {
    val categories: List<Color> get() = listOf(cat1, cat2, cat3, cat4, cat5)

    /**
     * Latency as a signal: fine, usable, slow. Tuned to what a tunnel out of
     * Russia actually measures — the old 40/100 ms steps painted every server
     * here the "slow" colour, which told nobody anything.
     */
    fun latencyColor(ms: Int): Color = when {
        ms < 0 -> textMuted
        ms < 150 -> statusUpInk
        ms < 300 -> statusDegradedInk
        else -> statusPartialInk
    }
}

private val Lime = Color(0xFFD2FF1F)
private val Ink = Color(0xFF0A0A0A)
private val Telegram = Color(0xFF29A0DA)

val MoonlightDarkColors = MoonlightColors(
    brand = Lime,
    brandInk = Ink,

    bg = Color.Black,
    bgDeep = Ink,
    surface = Color(0xFF111111),
    surface2 = Color(0xFF1A1A1A),
    surface3 = Color(0xFF262626),
    // A step above a card, so the floating bar still has an edge over one.
    surfaceNav = Color(0xFF161616),

    text = Color(0xFFF5F5F5),
    text2 = Color(0xFFA3A3A3),
    textMuted = Color(0xFF737373),
    textOnAccent = Color.Black,
    textLink = Color(0xFFF5F5F5),

    accent = Color.White,
    accentHover = Color(0xFFE5E5E5),
    accentQuiet = Color.White.copy(alpha = 0.08f),
    accentInk = Color.White,
    accentInkStrong = Color.White,
    accentLine = Color.White.copy(alpha = 0.6f),

    hairline = Color.White.copy(alpha = 0.10f),
    hairlineSoft = Color.White.copy(alpha = 0.06f),
    inkWash = Color.Black.copy(alpha = 0.10f),
    inkWashSoft = Color.Black.copy(alpha = 0.05f),

    danger = Color(0xFFFF453A),
    dangerQuiet = Color(0xFFFF453A).copy(alpha = 0.14f),
    warning = Color(0xFFFFD60A),
    info = Color(0xFFA3A3A3),
    heroGold = Color.White,

    cat1 = Color(0xFF1F1F1F),
    cat2 = Color(0xFF1F1F1F),
    cat3 = Color(0xFF1F1F1F),
    cat4 = Color(0xFF1F1F1F),
    cat5 = Color(0xFF1F1F1F),

    statusUpInk = Color(0xFF30D158),
    statusDegradedInk = Color(0xFFFFD60A),
    statusMaintenanceInk = Color(0xFFA3A3A3),
    statusPartialInk = Color(0xFFFF9F0A),
    statusDownInk = Color(0xFFFF453A),

    telegram = Telegram,
    isLight = false,
)

/** The same system inverted: a white canvas, black as the interactive colour. */
val MoonlightLightColors = MoonlightColors(
    brand = Lime,
    brandInk = Ink,

    bg = Color.White,
    bgDeep = Color(0xFFFAFAFA),
    surface = Color(0xFFF5F5F5),
    surface2 = Color(0xFFEDEDED),
    surface3 = Color(0xFFE0E0E0),
    surfaceNav = Color(0xFFF0F0F0),

    text = Ink,
    text2 = Color(0xFF525252),
    textMuted = Color(0xFF8A8A8A),
    textOnAccent = Color.White,
    textLink = Ink,

    accent = Ink,
    accentHover = Color(0xFF262626),
    accentQuiet = Color.Black.copy(alpha = 0.06f),
    accentInk = Ink,
    accentInkStrong = Ink,
    accentLine = Color.Black.copy(alpha = 0.5f),

    hairline = Color.Black.copy(alpha = 0.10f),
    hairlineSoft = Color.Black.copy(alpha = 0.06f),
    inkWash = Color.White.copy(alpha = 0.16f),
    inkWashSoft = Color.White.copy(alpha = 0.08f),

    danger = Color(0xFFD70015),
    dangerQuiet = Color(0xFFD70015).copy(alpha = 0.10f),
    warning = Color(0xFFB58900),
    info = Color(0xFF525252),
    heroGold = Ink,

    cat1 = Color(0xFFEDEDED),
    cat2 = Color(0xFFEDEDED),
    cat3 = Color(0xFFEDEDED),
    cat4 = Color(0xFFEDEDED),
    cat5 = Color(0xFFEDEDED),

    statusUpInk = Color(0xFF248A3D),
    statusDegradedInk = Color(0xFFB58900),
    statusMaintenanceInk = Color(0xFF525252),
    statusPartialInk = Color(0xFFC2410C),
    statusDownInk = Color(0xFFD70015),

    telegram = Telegram,
    isLight = true,
)
