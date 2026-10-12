package app.blesk.core.design.icon

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** How a tab's icon comes alive when the tab is chosen. */
enum class LivingIcon {
    /** The chat bubble fills with light. */
    Fill,

    /** The two people step towards each other. */
    Gather,

    /** The two capsules of a room move together and stick. */
    Stick,

    /** The sliders of the settings slide over. */
    Slide,
}

/**
 * A tab icon drawn on the same 24 grid and line as [BleskIcons], moving with [progress]: 0 at
 * rest, 1 once the tab is chosen.
 */
@Composable
fun LivingIcon(kind: LivingIcon, progress: () -> Float, tint: Color, light: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val parts = remember(kind) { kind.parts() }
    Canvas(modifier.size(size)) {
        val unit = this.size.minDimension / GRID
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(BleskIcons.STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)
            val p = progress()
            when (kind) {
                LivingIcon.Fill -> {
                    drawPath(parts[0], light.copy(alpha = light.alpha * FILL_ALPHA * p))
                    drawPath(parts[0], tint, style = stroke)
                }
                LivingIcon.Gather -> {
                    translate(left = GATHER_NEAR * p) { drawPath(parts[0], tint, style = stroke) }
                    translate(left = -GATHER_FAR * p) { drawPath(parts[1], tint, style = stroke) }
                }
                LivingIcon.Stick -> {
                    translate(left = STICK_X * p, top = STICK_Y * p) { capsule(parts[0], tint, light, p, stroke) }
                    translate(left = -STICK_X * p, top = -STICK_Y * p) { capsule(parts[1], tint, light, p, stroke) }
                }
                LivingIcon.Slide -> {
                    slider(y = TOP_ROW, knob = TOP_KNOB - SLIDE * p, tint = tint, stroke = stroke)
                    slider(y = BOTTOM_ROW, knob = BOTTOM_KNOB + SLIDE * p, tint = tint, stroke = stroke)
                }
            }
        }
    }
}

private fun DrawScope.capsule(path: Path, tint: Color, light: Color, p: Float, stroke: Stroke) {
    drawPath(path, light.copy(alpha = light.alpha * STICK_ALPHA * p))
    drawPath(path, tint, style = stroke)
}

/** A rail with a round knob on it; the rail stops short of the knob on both sides. */
private fun DrawScope.slider(y: Float, knob: Float, tint: Color, stroke: Stroke) {
    val ring = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset(knob, y), KNOB_R + KNOB_GAP)) }
    clipPath(ring, ClipOp.Difference) {
        drawLine(tint, Offset(RAIL_FROM, y), Offset(RAIL_TO, y), BleskIcons.STROKE, StrokeCap.Round)
    }
    drawCircle(tint, KNOB_R, Offset(knob, y), style = stroke)
}

private fun LivingIcon.parts(): List<Path> = when (this) {
    LivingIcon.Fill -> listOf(path(CHAT))
    LivingIcon.Gather -> listOf(path(PERSON_NEAR), path(PERSON_FAR))
    LivingIcon.Stick -> listOf(path(CAPSULE_A), path(CAPSULE_B))
    LivingIcon.Slide -> emptyList()
}

private fun path(data: String) = PathParser().parsePathString(data).toPath()

private const val GRID = 24f
private const val CHAT = "M4 11.5C4 7.4 7.6 4.5 12 4.5s8 2.9 8 7-3.6 7-8 7c-1 0-1.9-.1-2.8-.4L5 19.5l1-3.4C4.7 14.9 4 13.3 4 11.5z"
private const val PERSON_NEAR = "M9 11a3 3 0 1 0 0-6 3 3 0 0 0 0 6zM3.5 19c.6-3 2.7-5 5.5-5s4.9 2 5.5 5"
private const val PERSON_FAR = "M16 11a2.5 2.5 0 1 0 0-5M17.5 14.2c1.6.6 2.7 2.3 3 4.8"
private const val CAPSULE_A = "M4.5 9.5a3 3 0 0 1 3-3h4a3 3 0 0 1 0 6h-4a3 3 0 0 1-3-3z"
private const val CAPSULE_B = "M9.5 15.5a3 3 0 0 1 3-3h4a3 3 0 0 1 0 6h-4a3 3 0 0 1-3-3z"
private const val FILL_ALPHA = 0.55f
private const val STICK_ALPHA = 0.3f
private const val GATHER_NEAR = 0.8f
private const val GATHER_FAR = 1.6f
private const val STICK_X = 1.2f
private const val STICK_Y = 1.5f
private const val TOP_ROW = 7f
private const val BOTTOM_ROW = 17f
private const val TOP_KNOB = 15f
private const val BOTTOM_KNOB = 9f
private const val SLIDE = 4f
private const val KNOB_R = 2.5f
private const val KNOB_GAP = 1.5f
private const val RAIL_FROM = 4f
private const val RAIL_TO = 20f
