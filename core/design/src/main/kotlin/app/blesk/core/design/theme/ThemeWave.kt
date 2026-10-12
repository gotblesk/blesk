package app.blesk.core.design.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import app.blesk.core.motion.BleskMotion
import app.blesk.core.motion.Durations
import app.blesk.core.motion.Viscous
import app.blesk.core.motion.rememberSystemMotion
import app.blesk.shared.appearance.Appearance
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * The app theme, changed by a wave of light: the wave starts on the side the light comes from,
 * and behind its front everything is already in the new theme. The old look is a picture of the
 * last frame, wiped away by the front, so nothing is drawn twice. Without animations the theme
 * simply switches.
 */
@Composable
fun BleskThemeWithWave(
    appearance: Appearance,
    motion: BleskMotion = rememberSystemMotion(),
    content: @Composable () -> Unit,
) {
    var shown by remember { mutableStateOf(appearance) }
    var before by remember { mutableStateOf<ImageBitmap?>(null) }
    val wave = remember { Animatable(1f) }
    val frame = rememberGraphicsLayer()
    LaunchedEffect(appearance) {
        if (motion.reduced || appearance.theme == shown.theme) {
            shown = appearance
            return@LaunchedEffect
        }
        before = runCatching { frame.toImageBitmap() }.getOrNull()
        shown = appearance
        wave.snapTo(0f)
        wave.animateTo(1f, tween(Durations.SIGNATURE, easing = Viscous))
        before = null
    }
    BleskTheme(appearance = shown, motion = motion) {
        val light = Blesk.colors.light
        val source = Blesk.light.direction
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    frame.record { this@drawWithContent.drawContent() }
                    drawLayer(frame)
                    val old = before ?: return@drawWithContent
                    val dir = source.value
                    // Turn the canvas so the wave travels along +x, away from the light.
                    val angle = Math.toDegrees(atan2(-dir.y, -dir.x).toDouble()).toFloat()
                    val reach = hypot(size.width, size.height) / 2f
                    val front = center.x - reach + 2f * reach * wave.value
                    rotate(-angle, center) {
                        clipRect(front, center.y - reach, center.x + reach, center.y + reach) {
                            rotate(angle, center) { drawImage(old) }
                        }
                        val glow = reach * GLOW_SHARE
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, light.copy(alpha = GLOW_ALPHA), Color.Transparent),
                                startX = front - glow,
                                endX = front + glow / 2f,
                            ),
                            topLeft = Offset(front - glow, center.y - reach),
                            size = Size(glow * 1.5f, reach * 2f),
                        )
                    }
                },
        ) {
            content()
        }
    }
}

private const val GLOW_SHARE = 0.18f
private const val GLOW_ALPHA = 0.45f
