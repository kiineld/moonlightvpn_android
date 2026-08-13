package vpn.moonlight.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * The Moonlight palette. Mirrors `tokens/colors.css`.
 *
 * The accent deliberately splits into three roles. In dark mode all three are
 * the same lime; in light mode they diverge, because acid lime on near-white
 * neither fills nor reads. Collapsing them would break light mode:
 *
 *  - [accent]     fills — buttons, the dial sweep, active pills
 *  - [accentInk]  accent as type or a glyph on a light surface
 *  - [accentLine] accent as a thin mark — bars, dots, hairlines, focus rings
 */
@Immutable
data class MoonlightColors(
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

    /** Category fills for icon tiles, avatars and tariff cards. */
    val cat1: Color,
    val cat2: Color,
    val cat3: Color,
    val cat4: Color,
    val cat5: Color,

    /** Latency / service severities. `*Ink` is the readable-on-page variant. */
    val statusUpInk: Color,
    val statusDegradedInk: Color,
    val statusMaintenanceInk: Color,
    val statusPartialInk: Color,
    val statusDownInk: Color,

    val telegram: Color,
    val isLight: Boolean,
) {
    val categories: List<Color> get() = listOf(cat1, cat2, cat3, cat4, cat5)

    /** The design colour-codes latency: green under 40 ms, amber under 100. */
    fun latencyColor(ms: Int): Color = when {
        ms < 0 -> textMuted
        ms < 40 -> statusUpInk
        ms < 100 -> statusDegradedInk
        else -> statusPartialInk
    }
}

private val Lime = Color(0xFFD2FF1F)
private val LimeDeep = Color(0xFFC2F015)
private val Purple = Color(0xFFAB93E1)
private val Yellow = Color(0xFFFFE078)
private val Blue = Color(0xFFB6CAEB)
private val Orange = Color(0xFFFB7A54)
private val Red = Color(0xFFFF6B5A)

private val Grey100 = Color(0xFFAEB7C7)
private val Grey200 = Color(0xFF878EA8)
private val Grey500 = Color(0xFF2A3547)
private val Grey600 = Color(0xFF212B3B)
private val Grey700 = Color(0xFF182131)
private val Grey800 = Color(0xFF101828)
private val Grey900 = Color(0xFF0B111E)

val MoonlightDarkColors = MoonlightColors(
    bg = Grey800,
    bgDeep = Grey900,
    surface = Grey700,
    surface2 = Grey600,
    surface3 = Grey500,
    surfaceNav = Color(0xEB182131),

    text = Color.White,
    text2 = Grey100,
    textMuted = Grey200,
    textOnAccent = Grey800,
    textLink = Lime,

    accent = Lime,
    accentHover = LimeDeep,
    accentQuiet = Lime.copy(alpha = 0.13f),
    accentInk = Lime,
    accentInkStrong = Lime,
    accentLine = Lime,

    hairline = Color.White.copy(alpha = 0.09f),
    hairlineSoft = Color.White.copy(alpha = 0.05f),
    inkWash = Grey800.copy(alpha = 0.14f),
    inkWashSoft = Grey800.copy(alpha = 0.06f),

    danger = Red,
    dangerQuiet = Red.copy(alpha = 0.13f),
    warning = Yellow,
    info = Blue,
    heroGold = Color(0xFFEFAE2E),

    cat1 = Lime,
    cat2 = Purple,
    cat3 = Blue,
    cat4 = Yellow,
    cat5 = Orange,

    statusUpInk = Lime,
    statusDegradedInk = Yellow,
    statusMaintenanceInk = Blue,
    statusPartialInk = Orange,
    statusDownInk = Red,

    telegram = Color(0xFF29A0DA),
    isLight = false,
)

/**
 * Same system, flipped, with two deliberate departures from a mechanical
 * inversion: the accent becomes yellow rather than lime, and the category fills
 * keep their dark-theme hues because ink on a dark purple slab fails contrast.
 */
val MoonlightLightColors = MoonlightColors(
    bg = Color(0xFFF2F3ED),
    bgDeep = Color(0xFFE6E8DF),
    surface = Color.White,
    surface2 = Color(0xFFF1F3EB),
    surface3 = Color(0xFFE1E4D9),
    surfaceNav = Color(0xEBFFFFFF),

    text = Grey800,
    text2 = Color(0xFF475467),
    textMuted = Color(0xFF667085),
    textOnAccent = Grey800,
    textLink = Color(0xFF7A5600),

    accent = Yellow,
    accentHover = Color(0xFFF5CE52),
    accentQuiet = Color(0xFFB07908).copy(alpha = 0.16f),
    accentInk = Color(0xFFEFAE2E),
    accentInkStrong = Color(0xFF6B4A00),
    accentLine = Color(0xFFEFAE2E),

    hairline = Grey800.copy(alpha = 0.11f),
    hairlineSoft = Grey800.copy(alpha = 0.06f),
    inkWash = Grey800.copy(alpha = 0.14f),
    inkWashSoft = Grey800.copy(alpha = 0.06f),

    danger = Color(0xFFB42318),
    dangerQuiet = Red.copy(alpha = 0.13f),
    warning = Color(0xFF9A6A00),
    info = Blue,
    heroGold = Yellow,

    cat1 = Yellow,
    cat2 = Purple,
    cat3 = Blue,
    // Deepened so the yellow category stays distinct from the now-yellow accent.
    cat4 = Color(0xFFEFAE2E),
    cat5 = Orange,

    statusUpInk = Color(0xFF4C7A0F),
    statusDegradedInk = Color(0xFF9A6A00),
    statusMaintenanceInk = Color(0xFF3D6392),
    statusPartialInk = Color(0xFFC2410C),
    statusDownInk = Color(0xFFB42318),

    telegram = Color(0xFF29A0DA),
    isLight = true,
)
