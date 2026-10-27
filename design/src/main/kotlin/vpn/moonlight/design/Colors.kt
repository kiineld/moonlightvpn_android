package vpn.moonlight.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Moonlight palette — the web cabinet's, value for value (its
 * `styles/tokens.css`), so the app and the cabinet read as one product.
 *
 * The page carries the colour — pale yellow on the light theme, a dark grey that
 * deepens downwards on the dark one — and what sits on it is frosted glass: a
 * translucent pane with a light edge. Ink does the work: the main button of a
 * screen is solid ink, black on light and near-white on dark. The pale yellow
 * [highlight] marks what is on or chosen, the round plus and the lit moon. The
 * logo keeps its own lime ([brand]) in both themes.
 *
 * The token names are the ones every screen was written against:
 *
 *  - [accent]     the ink fill — buttons, the tab puck, active pills
 *  - [accentInk]  accent as type or a glyph on a pane
 *  - [accentLine] accent as a thin mark — bars, dots, focus rings
 *  - [hairline]   a pane's light edge; [hairlineSoft] divides rows inside one
 */
@Immutable
data class MoonlightColors(
    /** The logo's lime. Nothing else. */
    val brand: Color,
    val brandInk: Color,

    val bg: Color,
    val bgDeep: Color,
    /** The page is drawn top to bottom between these two. */
    val pageTop: Color,
    val pageBottom: Color,
    /** A pane: translucent, so the page shows through as frosted glass. */
    val surface: Color,
    /** Set into a pane: a field, a track, a table head. */
    val surface2: Color,
    val surface3: Color,
    /** What floats over moving content — the tab bar. */
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

    /** The pale yellow of what is on or chosen; ink is written on it. */
    val highlight: Color,
    /** The deeper step that holds a bar or the moon on a light pane. */
    val highlightFill: Color,
    val onHighlight: Color,

    val hairline: Color,
    val hairlineSoft: Color,
    val inkWash: Color,
    val inkWashSoft: Color,

    val success: Color,
    val danger: Color,
    val dangerQuiet: Color,
    val warning: Color,
    val info: Color,
    val heroGold: Color,

    /** Fills for the tile behind a row's glyph. */
    val cat1: Color,
    val cat2: Color,
    val cat3: Color,
    val cat4: Color,
    val cat5: Color,

    /** Latency / service severities. */
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
private val LogoInk = Color(0xFF0D0D0F)
private val Telegram = Color(0xFF29A0DA)
private val PaleYellow = Color(0xFFF6F4A2)
private val Ink = Color(0xFF111110)
private fun white(alpha: Float) = Color.White.copy(alpha = alpha)
private fun warmInk(alpha: Float) = Color(0xFF14140A).copy(alpha = alpha)

val MoonlightDarkColors = MoonlightColors(
    brand = Lime,
    brandInk = LogoInk,

    bg = Color(0xFF1A1A1D),
    bgDeep = Color(0xFF141416),
    pageTop = Color(0xFF232327),
    pageBottom = Color(0xFF141416),
    surface = white(0.06f),
    surface2 = white(0.07f),
    surface3 = white(0.16f),
    surfaceNav = Color(0xEB282826),

    text = Color(0xFFF5F5F0),
    text2 = Color(0xFFB9B9AE),
    textMuted = Color(0xFF7C7C72),
    textOnAccent = Ink,
    textLink = Color(0xFFF5F5F0),

    accent = Color(0xFFF5F5F0),
    accentHover = Color(0xFFDADAD2),
    accentQuiet = PaleYellow.copy(alpha = 0.14f),
    accentInk = PaleYellow,
    accentInkStrong = Color(0xFFF5F5F0),
    accentLine = PaleYellow.copy(alpha = 0.42f),

    highlight = PaleYellow,
    highlightFill = PaleYellow,
    onHighlight = Ink,

    hairline = white(0.09f),
    hairlineSoft = white(0.07f),
    inkWash = Color.Black.copy(alpha = 0.10f),
    inkWashSoft = Color.Black.copy(alpha = 0.05f),

    success = Color(0xFF3DD68C),
    danger = Color(0xFFFF6B61),
    dangerQuiet = Color(0xFFFF6B61).copy(alpha = 0.13f),
    warning = Color(0xFFF5B740),
    info = Color(0xFFB9B9AE),
    heroGold = PaleYellow,

    cat1 = white(0.08f),
    cat2 = white(0.08f),
    cat3 = white(0.08f),
    cat4 = white(0.08f),
    cat5 = white(0.08f),

    statusUpInk = Color(0xFF3DD68C),
    statusDegradedInk = Color(0xFFF5B740),
    statusMaintenanceInk = Color(0xFFB9B9AE),
    statusPartialInk = Color(0xFFFF9F0A),
    statusDownInk = Color(0xFFFF6B61),

    telegram = Telegram,
    isLight = false,
)

/** The cabinet's white theme: a cream page, white glass, black ink. */
val MoonlightLightColors = MoonlightColors(
    brand = Lime,
    brandInk = LogoInk,

    bg = Color(0xFFF8F8DE),
    bgDeep = Color(0xFFF8F8DE),
    pageTop = Color(0xFFFBFBE8),
    pageBottom = Color(0xFFF4F4D3),
    surface = white(0.64f),
    surface2 = white(0.75f),
    surface3 = warmInk(0.13f),
    surfaceNav = white(0.88f),

    text = Ink,
    text2 = Color(0xFF55554A),
    textMuted = Color(0xFF8D8D7E),
    textOnAccent = Color.White,
    textLink = Ink,

    accent = Ink,
    accentHover = Color(0xFF2B2B28),
    accentQuiet = Color(0xFFDDD64F).copy(alpha = 0.24f),
    accentInk = Ink,
    accentInkStrong = Ink,
    accentLine = Color(0xFFC2BB36).copy(alpha = 0.6f),

    highlight = PaleYellow,
    highlightFill = Color(0xFFDDD64F),
    onHighlight = Ink,

    hairline = white(0.96f),
    hairlineSoft = warmInk(0.07f),
    inkWash = Color.White.copy(alpha = 0.16f),
    inkWashSoft = Color.White.copy(alpha = 0.08f),

    success = Color(0xFF12894B),
    danger = Color(0xFFD5342C),
    dangerQuiet = Color(0xFFD5342C).copy(alpha = 0.09f),
    warning = Color(0xFFA8650A),
    info = Color(0xFF55554A),
    heroGold = Color(0xFFDDD64F),

    cat1 = white(0.8f),
    cat2 = white(0.8f),
    cat3 = white(0.8f),
    cat4 = white(0.8f),
    cat5 = white(0.8f),

    statusUpInk = Color(0xFF12894B),
    statusDegradedInk = Color(0xFFA8650A),
    statusMaintenanceInk = Color(0xFF55554A),
    statusPartialInk = Color(0xFFC2410C),
    statusDownInk = Color(0xFFD5342C),

    telegram = Telegram,
    isLight = true,
)
