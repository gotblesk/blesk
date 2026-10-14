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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
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
import app.blesk.core.design.fire.dithered
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
fun HeaderArt(style: HeaderStyle, photo: ImageBitmap?, modifier: Modifier = Modifier, hue: Float? = null) {
    Box(modifier.clearAndSetSemantics { }.dithered()) {
        HeaderFill(style, photo, hue)
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
            ) { HeaderFill(style, photo, hue) }
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

/**
 * What a chat lies on: nothing but the theme's background, one of the «Свет» compositions, a
 * flat tone, or your photo. Light and photo are softened, so bubbles keep their contrast.
 */
@Composable
fun ChatBackdrop(style: HeaderStyle?, photo: ImageBitmap?, modifier: Modifier = Modifier) {
    if (style == null) return
    Box(modifier.fillMaxSize().clearAndSetSemantics { }.dithered()) {
        when {
            style is HeaderStyle.Photo && photo != null -> {
                Image(photo, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
                val scrim = Blesk.colors.background.copy(alpha = PHOTO_SCRIM)
                Box(Modifier.matchParentSize().drawBehind { drawRect(scrim) })
            }
            style is HeaderStyle.Solid -> SolidFill(style.tone)
            style is HeaderStyle.Light -> LightFill(style.preset, onBackground = true)
        }
    }
}

/** Reads a stored chat background: `none`, `photo`, `light:N` or `solid:N`; null is the plain background. */
fun chatBackdropOf(stored: String): HeaderStyle? {
    val kind = stored.substringBefore(':')
    val value = stored.substringAfter(':', "").toIntOrNull() ?: 0
    return when (kind) {
        "photo" -> HeaderStyle.Photo
        "light" -> HeaderStyle.Light(value)
        "solid" -> HeaderStyle.Solid(value)
        else -> null
    }
}

/** The part of a header photo that stays in view when it is cropped to the header's shape. */
val LocalHeaderFocus = staticCompositionLocalOf<Alignment> { Alignment.Center }

/** Reads the focus out of a stored profile header, `photo:x:y` with each in -100..100; centre otherwise. */
fun headerFocusOf(stored: String): Alignment {
    val parts = stored.split(':')
    if (parts.firstOrNull() != "photo" || parts.size < FOCUS_PARTS) return Alignment.Center
    val x = parts[1].toIntOrNull() ?: 0
    val y = parts[2].toIntOrNull() ?: 0
    fun bias(value: Int) = value.coerceIn(-FOCUS_RANGE, FOCUS_RANGE) / FOCUS_RANGE.toFloat()
    return BiasAlignment(bias(x), bias(y))
}

/** The stored form of a header photo focused on ([x], [y]), each from -1 to 1. */
fun headerPhotoKey(x: Float, y: Float): String =
    "photo:${(x.coerceIn(-1f, 1f) * FOCUS_RANGE).toInt()}:${(y.coerceIn(-1f, 1f) * FOCUS_RANGE).toInt()}"

private const val FOCUS_RANGE = 100
private const val FOCUS_PARTS = 3

/** The stored form of [style], the inverse of [chatBackdropOf]. */
fun chatBackdropKey(style: HeaderStyle?): String = when (style) {
    null -> "none"
    HeaderStyle.Photo -> "photo"
    is HeaderStyle.Light -> "light:${style.preset}"
    is HeaderStyle.Solid -> "solid:${style.tone}"
}

@Composable
private fun BoxScope.HeaderFill(style: HeaderStyle, photo: ImageBitmap?, hue: Float?) {
    when {
        style is HeaderStyle.Photo && photo != null -> Image(
            photo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = LocalHeaderFocus.current,
            modifier = Modifier.matchParentSize(),
        )
        style is HeaderStyle.Solid -> SolidFill(style.tone)
        else -> LightFill((style as? HeaderStyle.Light)?.preset ?: 0, hue = hue)
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
private fun BoxScope.LightFill(preset: Int, onBackground: Boolean = false, hue: Float? = null) {
    val colors = Blesk.colors
    val pools = LightPresets[preset.mod(LightPresets.size)]
    val moving = !Blesk.motion.still
    val phase by rememberLoopPhase(LIGHT_PERIOD_MILLIS)
    // A person's own header glows in their hue, the one of their avatar; elsewhere the light is the theme's.
    val tinted = remember(colors, hue) {
        hue?.let {
            val base = Oklch.from(colors.background)
            val depthL = if (colors.isDark) base.l + HUE_DEPTH_LIFT else base.l - HUE_DEPTH_LIFT
            Oklch(depthL.coerceIn(0f, 1f), HUE_DEPTH_CHROMA, it).toColor() to headerLight(it, colors.isDark)
        }
    }
    // Behind a chat the light lies on the plain background and is gentler, so bubbles stay readable on it.
    val depth = tinted?.first ?: if (onBackground) colors.background else colors.surfaceStrong
    val dim = if (onBackground) BACKDROP_DIM else 1f
    val light = tinted?.second ?: colors.light
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
                            listOf(light.copy(alpha = pool.strength * dim), light.copy(alpha = 0f)),
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

/** The light of a header drawn in [hue]: the colour its pools glow with. */
fun headerLight(hue: Float, dark: Boolean): Color =
    Oklch(if (dark) HUE_LIGHT_DARK_L else HUE_LIGHT_L, HUE_LIGHT_CHROMA, hue).toColor()

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
private const val PHOTO_SCRIM = 0.55f
private const val BACKDROP_DIM = 0.6f
private const val HUE_DEPTH_LIFT = 0.08f
private const val HUE_DEPTH_CHROMA = 0.05f
private const val HUE_LIGHT_L = 0.9f
private const val HUE_LIGHT_DARK_L = 0.72f
private const val HUE_LIGHT_CHROMA = 0.12f
