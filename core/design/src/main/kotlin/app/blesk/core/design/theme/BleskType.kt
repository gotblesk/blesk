package app.blesk.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.blesk.core.design.R
import kotlin.math.roundToInt

/** Onest is variable (100–900); each weight is an instance of the one bundled file. */
private fun onest(weight: Int) = Font(
    resId = R.font.onest,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Onest = FontFamily(
    onest(400),
    onest(450),
    onest(500),
    onest(560),
    onest(600),
    // Every step a condensing heading passes through, so its weight grows smoothly rather than jumping.
    onest(625),
    onest(650),
    onest(675),
    onest(700),
    onest(725),
    onest(750),
    onest(800),
)

/**
 * [style] tightening as a heading gathers into a lens: the weight grows from 600 to 750 in
 * steps of 25 and the tracking narrows, so a smaller heading keeps its presence.
 */
fun condense(style: TextStyle, fraction: Float): TextStyle {
    val f = fraction.coerceIn(0f, 1f)
    val weight = ((CONDENSE_FROM + (CONDENSE_TO - CONDENSE_FROM) * f) / CONDENSE_STEP).roundToInt() * CONDENSE_STEP
    return style.copy(fontWeight = FontWeight(weight), letterSpacing = (TRACK_FROM + (TRACK_TO - TRACK_FROM) * f).em)
}

private const val CONDENSE_FROM = 600f
private const val CONDENSE_TO = 750f
private const val CONDENSE_STEP = 25
private const val TRACK_FROM = -0.02f
private const val TRACK_TO = -0.035f

/** Large, dense headings; calm body text. */
@Immutable
data class BleskType(
    val display: TextStyle,
    val title: TextStyle,
    val headline: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    val overline: TextStyle,
) {
    companion object {
        private val trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

        private fun style(size: Float, line: Float, weight: Int, tracking: TextUnit, scale: Float) = TextStyle(
            fontFamily = Onest,
            fontWeight = FontWeight(weight),
            fontSize = (size * scale).sp,
            lineHeight = (line * scale).sp,
            letterSpacing = tracking,
            lineHeightStyle = trim,
            // Times, counters and sizes keep their width as digits change.
            fontFeatureSettings = "tnum",
        )

        fun create(scale: Float = 1f) = BleskType(
            display = style(32f, 38f, 600, (-0.02).em, scale),
            title = style(22f, 28f, 600, (-0.015).em, scale),
            headline = style(17f, 22f, 500, (-0.005).em, scale),
            body = style(16f, 22f, 400, 0.em, scale),
            bodyStrong = style(16f, 22f, 560, 0.em, scale),
            label = style(13f, 18f, 500, 0.005.em, scale),
            caption = style(11f, 14f, 450, 0.01.em, scale),
            overline = style(11f, 14f, 620, 0.08.em, scale),
        )
    }
}
