package app.blesk.core.design.component

import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk
import kotlin.math.hypot

/**
 * White glint, the mark of protection: a thin highlight running along the edge of an
 * end-to-end encrypted capsule, brightest on the side facing the light. Only E2EE chats and
 * closed rooms carry it; everything else stays matte. [color] and [onDark] override the current
 * theme, for previews of other themes.
 */
fun Modifier.glint(
    shape: Shape,
    enabled: Boolean = true,
    color: Color = Color.Unspecified,
    onDark: Boolean? = null,
): Modifier = if (!enabled) this else composed {
    val light = if (color.isSpecified) color else Blesk.colors.light
    val strength = Blesk.appearance.glintStrength
    val source = Blesk.light.direction
    val isDark = onDark ?: Blesk.colors.isDark
    glintDraw(shape, light, strength * if (isDark) 1f else LIGHT_THEME_BOOST, source)
}

private const val LIGHT_THEME_BOOST = 1.15f

private fun Modifier.glintDraw(
    shape: Shape,
    light: Color,
    strength: Float,
    source: State<Offset>,
): Modifier {
    if (strength <= 0f) return this
    return drawWithCache {
        val stroke = 1.dp.toPx()
        val inset = stroke / 2f
        val outline = shape.createOutline(
            size.copy(width = size.width - stroke, height = size.height - stroke),
            layoutDirection,
            this,
        )
        onDrawWithContent {
            drawContent()
            val dir = source.value
            val length = hypot(dir.x, dir.y).coerceAtLeast(0.001f)
            val ux = dir.x / length
            val uy = dir.y / length
            val cx = size.width / 2f
            val cy = size.height / 2f
            val brush = Brush.linearGradient(
                0f to light.copy(alpha = (0.85f * strength).coerceAtMost(1f)),
                0.32f to light.copy(alpha = 0.16f * strength),
                0.68f to Color.Transparent,
                1f to light.copy(alpha = 0.22f * strength),
                start = Offset(cx + ux * size.width / 2f, cy + uy * size.height / 2f),
                end = Offset(cx - ux * size.width / 2f, cy - uy * size.height / 2f),
            )
            translate(inset, inset) {
                drawOutline(outline, brush, style = Stroke(width = stroke))
            }
        }
    }
}
