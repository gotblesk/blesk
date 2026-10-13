package app.blesk.core.design.spark

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.dp
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.Durations
import app.blesk.core.motion.KeyboardEasing

/**
 * Key verification: two shapes, one for each person, come together. When the keys match they run
 * into one and catch fire; when they do not, they touch and spring apart. A new [replay] plays it
 * again.
 */
@Composable
fun KeyMerge(matched: Boolean, replay: Int, modifier: Modifier = Modifier) {
    val motion = Blesk.motion
    val reduced = motion.reduced
    val approach = remember(replay) { Animatable(if (reduced) 1f else 0f) }
    var lit by remember(replay) { mutableStateOf(false) }
    LaunchedEffect(replay, matched) {
        approach.animateTo(1f, tween(Durations.SIGNATURE, easing = KeyboardEasing))
        if (matched) {
            lit = true
        } else {
            // They spring apart as a stretched bridge snaps back.
            approach.animateTo(0f, motion.stretch())
        }
    }
    val color = Blesk.colors.surfaceStrong
    Box(modifier.fillMaxWidth().height(Height), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(Height)
                .metaballs(color, smoothness = 28.dp) {
                    val side = size.height
                    val reach = (size.width / 2f - side) * (1f - approach.value)
                    val center = size.width / 2f
                    // Matching keys end as one wide capsule; the others never get closer than touching.
                    val overlap = if (matched) side * 0.5f * approach.value else 0f
                    listOf(
                        Blob(Rect(center - side - reach + overlap, 0f, center - reach + overlap, side), side / 2f),
                        Blob(Rect(center + reach - overlap, 0f, center + side + reach - overlap, side), side / 2f),
                    )
                },
        )
        Box(Modifier.fillMaxWidth(MERGED_WIDTH).height(Height).flashOn(key = lit, shape = CircleShape))
    }
}

private val Height = 72.dp
private const val MERGED_WIDTH = 0.45f
