package app.blesk.core.design.fire

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/**
 * Fire: the only way colour appears. When [key] changes (sent, delivered, connected, keys
 * verified) a short spectral flash runs across the element's edge. The first value of [key]
 * does not flash, so opening a screen never sets anything alight.
 */
fun Modifier.flashOn(key: Any?, shape: Shape): Modifier = composed {
    val spectrum = Blesk.colors.spectrum
    val reduced = Blesk.motion.reduced
    val progress = remember { Animatable(1f) }
    var seen by remember { mutableStateOf(key) }
    LaunchedEffect(key) {
        if (key == seen) return@LaunchedEffect
        seen = key
        if (reduced) {
            progress.snapTo(0.5f)
            delay(STATIC_FLASH_MILLIS)
            progress.snapTo(1f)
        } else {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(FLASH_MILLIS, easing = LinearEasing))
        }
    }
    drawWithCache {
        val stroke = 1.5.dp.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val colors = spectrum + spectrum.first()
        onDrawWithContent {
            drawContent()
            val t = progress.value
            if (t >= 1f) return@onDrawWithContent
            val envelope = sin(t * PI).toFloat()
            val travel = size.width * 2f
            val start = -size.width + travel * t
            val brush = Brush.linearGradient(
                colors = colors.map { it.copy(alpha = envelope) },
                start = Offset(start, 0f),
                end = Offset(start + size.width, size.height),
                tileMode = TileMode.Mirror,
            )
            drawOutline(outline, brush, alpha = 0.12f * envelope)
            drawOutline(outline, brush, style = Stroke(stroke))
        }
    }
}

private const val FLASH_MILLIS = 400
private const val STATIC_FLASH_MILLIS = 300L
