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
    onest(620),
    onest(700),
    onest(800),
)

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
        )

        fun create(scale: Float = 1f) = BleskType(
            display = style(36f, 38f, 800, (-0.035).em, scale),
            title = style(26f, 30f, 700, (-0.025).em, scale),
            headline = style(19f, 24f, 620, (-0.01).em, scale),
            body = style(16f, 22f, 400, 0.em, scale),
            bodyStrong = style(16f, 22f, 560, 0.em, scale),
            label = style(14f, 18f, 500, 0.005.em, scale),
            caption = style(12.5f, 16f, 450, 0.01.em, scale),
            overline = style(11.5f, 14f, 620, 0.08.em, scale),
        )
    }
}
