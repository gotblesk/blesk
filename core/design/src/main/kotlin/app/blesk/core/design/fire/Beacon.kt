package app.blesk.core.design.fire

import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk

/**
 * «Маяк»: while a call is ringing, light goes slowly round the edge of the avatar like a
 * lighthouse beam, instead of circles spreading out. Without motion the edge just glows.
 */
fun Modifier.beacon(active: Boolean): Modifier = composed {
    if (!active) return@composed this
    val light = Blesk.colors.light
    val phase = rememberLoopPhase(BEACON_PERIOD_MILLIS)
    val still = Blesk.motion.still
    drawWithContent {
        drawContent()
        val stroke = BEACON_WIDTH.toPx()
        val radius = size.minDimension / 2f + stroke
        val beam = Brush.sweepGradient(
            0f to light.copy(alpha = 0f),
            BEAM_END to light,
            BEAM_END + 0.001f to light.copy(alpha = 0f),
            1f to light.copy(alpha = 0f),
            center = center,
        )
        if (still) {
            drawCircle(light.copy(alpha = STILL_ALPHA), radius, style = Stroke(stroke))
        } else {
            rotate(phase.value * FULL_TURN) { drawCircle(beam, radius, style = Stroke(stroke)) }
        }
    }
}

private val BEACON_WIDTH = 3.dp
private const val BEACON_PERIOD_MILLIS = 2_400
private const val BEAM_END = 0.3f
private const val FULL_TURN = 360f
private const val STILL_ALPHA = 0.6f
