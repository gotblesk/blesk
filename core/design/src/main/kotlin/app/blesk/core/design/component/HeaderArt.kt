package app.blesk.core.design.component

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** What fills a profile header. */
@Immutable
sealed interface HeaderStyle {
    /** An abstract composition of light, drawn in the theme's colours. [preset] is 0 until [Light.PRESETS]. */
    data class Light(val preset: Int) : HeaderStyle {
        companion object {
            const val PRESETS = 6
        }
    }

    /** One flat colour from the theme's palette; [tone] is 0 until [Solid.TONES]. */
    data class Solid(val tone: Int) : HeaderStyle {
        companion object {
            const val TONES = 8
        }
    }

    data object Photo : HeaderStyle
}

/**
 * A profile header that melts into the background at its lower edge: on Android 12 and later a
 * blurred copy fades in towards the bottom, so the blur grows progressively; everywhere the
 * colour fades into the background so there is never a hard border.
 */
@Composable
fun HeaderArt(style: HeaderStyle, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    Box(modifier.clearAndSetSemantics { }) {
        HeaderFill(style, photo)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(BlurRadius.toPx(), BlurRadius.toPx(), TileMode.Clamp)
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            Brush.verticalGradient(0f to Color.Transparent, BLUR_FROM to Color.Transparent, 1f to Color.Black),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            ) { HeaderFill(style, photo) }
        }
        val background = Blesk.colors.background
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to Color.Transparent, FADE_FROM to Color.Transparent, 1f to background))
                },
        )
    }
}

@Composable
private fun BoxScope.HeaderFill(style: HeaderStyle, photo: ImageBitmap?) {
    when {
        style is HeaderStyle.Photo && photo != null ->
            Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        style is HeaderStyle.Solid -> SolidFill(style.tone)
        else -> LightFill((style as? HeaderStyle.Light)?.preset ?: 0)
    }
}

@Composable
private fun BoxScope.SolidFill(tone: Int) {
    val color = rememberTone(tone)
    Box(Modifier.matchParentSize().drawBehind { drawRect(color) })
}

/** One of eight tones at the same lightness, built from the theme's background. */
@Composable
fun rememberTone(tone: Int): Color {
    val colors = Blesk.colors
    return remember(colors, tone) {
        val base = Oklch.from(colors.background)
        val lightness = if (colors.isDark) base.l + TONE_LIFT else base.l - TONE_LIFT
        Oklch(lightness.coerceIn(0f, 1f), TONE_CHROMA, tone.mod(HeaderStyle.Solid.TONES) * (360f / HeaderStyle.Solid.TONES)).toColor()
    }
}

/**
 * Pools of the theme's light drifting slowly over a deep surface. Each preset places the pools
 * differently; with animations off or in battery saver the light stands still.
 */
@Composable
private fun BoxScope.LightFill(preset: Int) {
    val colors = Blesk.colors
    val pools = LightPresets[preset.mod(LightPresets.size)]
    val moving = !Blesk.motion.still
    val phase by rememberLoopPhase(LIGHT_PERIOD_MILLIS)
    val depth = colors.surfaceStrong
    val light = colors.light
    Box(
        Modifier
            .matchParentSize()
            .drawBehind {
                drawRect(depth)
                val t = if (moving) phase * 2f * PI.toFloat() else 0f
                pools.forEachIndexed { index, pool ->
                    val drift = 0.04f
                    val center = Offset(
                        size.width * (pool.x + drift * cos(t + index * 1.7f)),
                        size.height * (pool.y + drift * sin(t * 0.8f + index)),
                    )
                    val radius = size.maxDimension * pool.radius
                    drawCircle(
                        Brush.radialGradient(
                            listOf(light.copy(alpha = pool.strength), light.copy(alpha = 0f)),
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            },
    )
}

private class Pool(val x: Float, val y: Float, val radius: Float, val strength: Float)

private val LightPresets = listOf(
    listOf(Pool(0.2f, 0.2f, 0.6f, 0.45f), Pool(0.85f, 0.7f, 0.45f, 0.25f)),
    listOf(Pool(0.5f, 0.0f, 0.7f, 0.5f), Pool(0.1f, 0.9f, 0.35f, 0.18f)),
    listOf(Pool(0.85f, 0.15f, 0.55f, 0.4f), Pool(0.25f, 0.65f, 0.5f, 0.3f), Pool(0.6f, 0.95f, 0.3f, 0.15f)),
    listOf(Pool(0.0f, 0.5f, 0.65f, 0.45f), Pool(1.0f, 0.5f, 0.65f, 0.45f)),
    listOf(Pool(0.5f, 0.55f, 0.4f, 0.55f)),
    listOf(Pool(0.15f, 0.1f, 0.35f, 0.35f), Pool(0.5f, 0.45f, 0.35f, 0.3f), Pool(0.85f, 0.8f, 0.35f, 0.25f)),
)

private val BlurRadius = 24.dp
private const val BLUR_FROM = 0.45f
private const val FADE_FROM = 0.55f
private const val LIGHT_PERIOD_MILLIS = 14_000
private const val TONE_LIFT = 0.12f
private const val TONE_CHROMA = 0.07f
