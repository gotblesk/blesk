package app.blesk.core.design.motion

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import app.blesk.core.design.theme.Blesk

/**
 * A 0..1 phase that loops forever, or stays at zero when animations are off. Nothing loops
 * when the system asks for reduced motion.
 */
@Composable
fun rememberLoopPhase(durationMillis: Int): State<Float> {
    if (Blesk.motion.reduced) return remember { mutableFloatStateOf(0f) }
    val transition = rememberInfiniteTransition(label = "loop")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis, easing = LinearEasing)),
        label = "phase",
    )
}
