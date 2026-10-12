package app.blesk.core.design.fire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.delay

/**
 * One pass of the theme's light across the element when [active] turns on: the message a
 * search led to, so the eye finds it. With reduced motion the element glows briefly instead.
 */
fun Modifier.lightSweep(active: Boolean, shape: Shape): Modifier = composed {
    val light = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) SWEEP_DARK else SWEEP_LIGHT)
    val reduced = Blesk.motion.reduced
    val progress = remember { Animatable(1f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        if (reduced) {
            progress.snapTo(0.5f)
            delay(STILL_MILLIS)
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(SWEEP_MILLIS, easing = LinearEasing))
        }
    }
    drawWithCache {
        val outline = shape.createOutline(size, layoutDirection, this)
        onDrawWithContent {
            drawContent()
            val t = progress.value
            if (t >= 1f) return@onDrawWithContent
            val band = size.width * BAND
            val x = -band + (size.width + band * 2f) * t
            val brush = if (reduced) {
                Brush.horizontalGradient(listOf(light, light))
            } else {
                Brush.horizontalGradient(listOf(light.copy(alpha = 0f), light, light.copy(alpha = 0f)), startX = x - band, endX = x + band)
            }
            drawOutline(outline, brush)
        }
    }
}

private const val SWEEP_MILLIS = 700
private const val STILL_MILLIS = 400L
private const val BAND = 0.4f
private const val SWEEP_LIGHT = 0.55f
private const val SWEEP_DARK = 0.25f
