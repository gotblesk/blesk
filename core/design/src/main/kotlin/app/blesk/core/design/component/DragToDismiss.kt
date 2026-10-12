package app.blesk.core.design.component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch

/**
 * A sheet that follows the finger down and closes by the speed of the swipe: a quick flick
 * closes it from anywhere, a slow pull only past a third of its height. Pulling up meets
 * resistance; let go short of the threshold and it settles back.
 */
@Composable
fun Modifier.dragToDismiss(onDismiss: () -> Unit): Modifier {
    val offset = remember { Animatable(0f) }
    val height = remember { floatArrayOf(1f) }
    val scope = rememberCoroutineScope()
    val motion = Blesk.motion
    val fling = with(LocalDensity.current) { FlingSpeed.toPx() }
    val state = rememberDraggableState { delta ->
        val next = offset.value + if (offset.value + delta < 0f) delta * UPWARD_RESISTANCE else delta
        scope.launch { offset.snapTo(next) }
    }
    return this
        .onSizeChanged { height[0] = it.height.toFloat().coerceAtLeast(1f) }
        .graphicsLayer { translationY = offset.value }
        .draggable(
            state = state,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
                if (velocity > fling || offset.value > height[0] * CLOSE_SHARE) {
                    offset.animateTo(height[0], motion.transition(), initialVelocity = velocity)
                    onDismiss()
                } else {
                    offset.animateTo(0f, motion.settle(), initialVelocity = velocity)
                }
            },
        )
}

private val FlingSpeed = 1_200.dp
private const val CLOSE_SHARE = 0.33f
private const val UPWARD_RESISTANCE = 0.25f
