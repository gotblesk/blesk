package app.blesk.core.design.fire

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import app.blesk.core.design.theme.Blesk

/**
 * The capsule breathes with the voice: the edge of a speaker's avatar or tile swells by a few
 * percent with how loud they are, and stands still in silence. Drawn, never laid out again.
 */
fun Modifier.breathing(level: Float): Modifier = composed {
    val reduced = Blesk.motion.reduced
    val swell by animateFloatAsState(if (reduced) 0f else level.coerceIn(0f, 1f), Blesk.motion.select(), label = "breath")
    graphicsLayer {
        val scale = 1f + BREATH * swell
        scaleX = scale
        scaleY = scale
    }
}

private const val BREATH = 0.06f
