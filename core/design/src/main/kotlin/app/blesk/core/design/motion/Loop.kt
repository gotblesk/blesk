package app.blesk.core.design.motion

import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.flow.first

/**
 * Whether the screen around is moving as a whole: opening, closing or following the back
 * gesture. Read only inside effects and drawing, so a screen does not recompose when it starts
 * or stops moving.
 */
val LocalScreenMoving = staticCompositionLocalOf<() -> Boolean> { { false } }

/**
 * A 0..1 phase that loops forever, or stays at zero when animations are off. Nothing loops
 * when the system asks for reduced motion, and the loop holds still while its screen moves, so
 * a screen in a transition is only transformed, never redrawn.
 */
@Composable
fun rememberLoopPhase(durationMillis: Int): State<Float> {
    val phase = remember { mutableFloatStateOf(0f) }
    if (Blesk.motion.reduced) return phase
    val moving = LocalScreenMoving.current
    LaunchedEffect(durationMillis, moving) {
        val periodNanos = durationMillis * NANOS_PER_MILLI
        while (true) {
            if (moving()) snapshotFlow { moving() }.first { !it }
            var last = withInfiniteAnimationFrameNanos { it }
            while (!moving()) {
                val now = withInfiniteAnimationFrameNanos { it }
                phase.floatValue = (phase.floatValue + (now - last).toFloat() / periodNanos) % 1f
                last = now
            }
        }
    }
    return phase
}

private const val NANOS_PER_MILLI = 1_000_000f
