package vpn.moonlight.design

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Onest carries every UI and body string; Unbounded is display only — page
 * titles, hero numbers, plan names, stat values, the wordmark. Unbounded never
 * appears below 15sp and never in running text.
 *
 * Both ship as variable fonts, so each weight is a named instance on the
 * `wght` axis rather than a separate file.
 */
@OptIn(ExperimentalTextApi::class)
private fun variableFont(resId: Int, weight: Int) = Font(
    resId = resId,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val OnestFamily = FontFamily(
    variableFont(R.font.onest_variable, 400),
    variableFont(R.font.onest_variable, 500),
    variableFont(R.font.onest_variable, 600),
    variableFont(R.font.onest_variable, 700),
    variableFont(R.font.onest_variable, 800),
)

val UnboundedFamily = FontFamily(
    variableFont(R.font.unbounded_variable, 700),
    variableFont(R.font.unbounded_variable, 800),
)

val MonoFamily = FontFamily.Monospace

/** Weights run heavy: 500 is the lightest body weight, 800 the default for anything emphatic. */
object MlWeight {
    val Body = FontWeight(500)
    val Medium = FontWeight(600)
    val Title = FontWeight(700)
    val Emphatic = FontWeight(800)
}

private val TightLineHeight = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun display(size: Float, lineHeight: Float, tracking: Float = -0.03f) = TextStyle(
    fontFamily = UnboundedFamily,
    fontWeight = MlWeight.Emphatic,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
    lineHeightStyle = TightLineHeight,
)

private fun text(
    size: Float,
    weight: FontWeight = MlWeight.Body,
    lineHeight: Float = 1.5f,
    tracking: Float = 0f,
) = TextStyle(
    fontFamily = OnestFamily,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * lineHeight).sp,
    letterSpacing = tracking.em,
)

@Immutable
data class MoonlightTypography(
    /** Balance / big value. */
    val hero: TextStyle = display(40f, 1.05f),
    /** Plan name in the mobile hero. */
    val plan: TextStyle = display(30f, 1.12f, -0.035f),
    /** Page title, stat value. */
    val title: TextStyle = display(24f, 1.1f),
    /** Screen sub-header, e.g. a pushed detail screen. */
    val titleSm: TextStyle = display(20f, 1.1f),
    /** Card headline. */
    val lead: TextStyle = display(19f, 1.1f),

    /** Row title, button label, input value. */
    val body: TextStyle = text(15f),
    val bodyTitle: TextStyle = text(15f, MlWeight.Title),
    val bodyEmphatic: TextStyle = text(15f, MlWeight.Emphatic),
    /** Secondary body, key-value rows, feature rows. */
    val bodySm: TextStyle = text(14f),
    val bodySmTitle: TextStyle = text(14f, MlWeight.Title),
    val bodySmEmphatic: TextStyle = text(14.5f, MlWeight.Emphatic),
    /** Row sub-line, captions. */
    val meta: TextStyle = text(12.5f, lineHeight = 1.4f),
    val metaEmphatic: TextStyle = text(12.5f, MlWeight.Emphatic, lineHeight = 1.4f),
    val metaSm: TextStyle = text(12f, lineHeight = 1.4f),
    /** Overline, chip, hero stat label. Callers uppercase the string. */
    val overline: TextStyle = text(11.5f, MlWeight.Emphatic, 1.2f, tracking = 0.1f),
    val overlineSm: TextStyle = text(10.5f, MlWeight.Emphatic, 1.2f, tracking = 0.06f),

    val mono: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = MlWeight.Medium,
        fontSize = 15.sp,
        letterSpacing = 0.02f.em,
    ),
    val monoSm: TextStyle = TextStyle(
        fontFamily = MonoFamily,
        fontWeight = MlWeight.Medium,
        fontSize = 12.5.sp,
    ),
)
