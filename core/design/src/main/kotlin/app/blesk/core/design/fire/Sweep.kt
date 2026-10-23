package app.blesk.core.design.fire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.contrastRatio
import app.blesk.core.design.motion.rememberLoopPhase
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

/**
 * Something is being done and the element waits for it, like a button that started creating an
 * account: while [active] a veil of the page dims the element's [fill] and the theme's light
 * crosses it again and again; where the light must stand still there is only the veil. Both give
 * way to the [content] written on the element: dimmed, it keeps the enhanced contrast, which
 * leaves the light room to pass, and wherever the light is, it still reads as body text. In the
 * light theme the words on a dark button are almost the light's own colour, and the light would
 * otherwise wash them out. Placed after the fill, so both go under the content and it stays sharp.
 */
fun Modifier.passingLight(active: Boolean, shape: Shape, fill: Color, content: Color): Modifier {
    if (!active) return this
    return composed {
        val colors = Blesk.colors
        val most = if (colors.isDark) PASS_DARK else PASS_LIGHT
        val layers = remember(colors.background, colors.light, most, fill, content) {
            val veil = colors.background.copy(alpha = mostOver(colors.background, fill, content, ContrastLevel.ENHANCED_TEXT, VEIL))
            val light = colors.light.copy(alpha = mostOver(colors.light, veil.compositeOver(fill), content, ContrastLevel.BODY_TEXT, most))
            veil to light
        }
        val (veil, light) = layers
        val still = Blesk.motion.still
        val phase by rememberLoopPhase(PASS_MILLIS)
        drawWithCache {
            val outline = shape.createOutline(size, layoutDirection, this)
            onDrawWithContent {
                drawOutline(outline, veil)
                if (!still) {
                    val band = size.width * BAND
                    val x = -band + (size.width + band * 2f) * phase
                    val glow = listOf(light.copy(alpha = 0f), light, light.copy(alpha = 0f))
                    drawOutline(outline, Brush.horizontalGradient(glow, startX = x - band, endX = x + band))
                }
                drawContent()
            }
        }
    }
}

/**
 * How much of [layer], up to [most], can lie over [base] with [content] on it still at [ratio].
 * Every lesser amount must keep it too: the light's soft edges show each of them somewhere.
 */
private fun mostOver(layer: Color, base: Color, content: Color, ratio: Float, most: Float): Float =
    (1..ALPHA_STEPS).map { most * it / ALPHA_STEPS }
        .takeWhile { alpha -> contrastRatio(content, layer.copy(alpha = alpha).compositeOver(base)) >= ratio }
        .lastOrNull() ?: 0f

private const val SWEEP_MILLIS = 700
private const val PASS_MILLIS = 1_600
private const val VEIL = 0.4f
private const val PASS_LIGHT = 0.6f
private const val PASS_DARK = 0.4f
private const val STILL_MILLIS = 400L
private const val BAND = 0.4f
private const val ALPHA_STEPS = 40
private const val SWEEP_LIGHT = 0.55f
private const val SWEEP_DARK = 0.25f
