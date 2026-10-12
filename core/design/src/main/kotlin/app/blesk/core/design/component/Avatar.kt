package app.blesk.core.design.component

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.color.ensureContrast
import app.blesk.core.design.theme.Blesk
import kotlin.math.atan2
import kotlin.math.cos

/**
 * A monogram avatar: the first letter of the name on a colour of its own. Every person gets a
 * hue from their name, all at the same OKLCH lightness, so no one stands out. People set a
 * photo; until then there are no generated faces or placeholders.
 *
 * [protected] adds the white glint of end-to-end encryption: a short highlight on the rim
 * facing the light, brightest in the middle, following the tilt of the phone.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = Blesk.dimens.avatar,
    photo: ImageBitmap? = null,
    protected: Boolean = false,
    key: String = name,
) {
    val letter = name.trim().firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString().orEmpty()
    val dark = Blesk.colors.isDark
    val fill = remember(key, dark) { hueFor(key, dark) }
    val ink =
        remember(fill) { ensureContrast(if (dark) Color.White else Color.Black, fill, ContrastLevel.BODY_TEXT).copy(alpha = 0.86f) }
    val light = Blesk.colors.light
    val strength = Blesk.appearance.glintStrength
    val source = Blesk.light.direction
    Box(
        modifier = modifier
            .size(size)
            .let { if (protected && strength > 0f) it.rimGlint(light, strength) { source.value } else it }
            .background(fill, CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().clip(CircleShape),
            )
            return@Box
        }
        Text(
            text = letter,
            style = Blesk.type.headline.copy(fontSize = Blesk.type.headline.fontSize * (size.value / 48f)),
            color = ink,
            maxLines = 1,
        )
    }
}

/** The colour a monogram avatar of [key] sits on, for shapes that grow out of it. */
@Composable
fun rememberAvatarFill(key: String): Color {
    val dark = Blesk.colors.isDark
    return remember(key, dark) { hueFor(key, dark) }
}

/**
 * Text in the same hue as [key]'s monogram, readable on the background: an author's name in a
 * group is coloured like their avatar, so a column is matched to its face at a glance.
 */
@Composable
fun rememberAvatarInk(key: String): Color {
    val dark = Blesk.colors.isDark
    val background = Blesk.colors.background
    return remember(key, dark, background) {
        val ink = Oklch(if (dark) INK_DARK_L else INK_LIGHT_L, INK_CHROMA, hueOf(key)).toColor()
        ensureContrast(ink, background, ContrastLevel.BODY_TEXT)
    }
}

/** Same lightness and chroma for everyone, only the hue differs. */
private fun hueFor(key: String, dark: Boolean): Color =
    Oklch(if (dark) 0.47f else 0.86f, if (dark) 0.085f else 0.06f, hueOf(key)).toColor()

private fun hueOf(key: String) = (key.hashCode().toUInt().toLong() % HUE_STEPS) * (360f / HUE_STEPS)

private const val INK_LIGHT_L = 0.48f
private const val INK_DARK_L = 0.8f
private const val INK_CHROMA = 0.11f

/** A short arc of light over the rim, drawn above the photo or letter. */
private fun Modifier.rimGlint(light: Color, strength: Float, direction: () -> Offset): Modifier = drawWithContent {
    drawContent()
    val dir = direction()
    val center = Math.toDegrees(atan2(dir.y.toDouble(), dir.x.toDouble())).toFloat()
    val stroke = (size.minDimension * RIM_SHARE).coerceAtLeast(1.5.dp.toPx())
    val inset = stroke / 2f
    val arcSize = Size(size.width - stroke, size.height - stroke)
    // The arc is drawn in slices so it fades out towards both ends.
    for (i in 0 until SLICES) {
        val t = (i + 0.5f) / SLICES * 2f - 1f
        val alpha = (cos(t * Math.PI / 2).toFloat()).let { it * it } * strength
        drawArc(
            color = light.copy(alpha = alpha.coerceIn(0f, 1f)),
            startAngle = center - SPAN / 2f + i * (SPAN / SLICES),
            sweepAngle = SPAN / SLICES + 0.5f,
            useCenter = false,
            topLeft = Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = stroke, cap = StrokeCap.Butt),
        )
    }
}

private const val HUE_STEPS = 24
private const val SPAN = 80f
private const val SLICES = 16
private const val RIM_SHARE = 0.045f
