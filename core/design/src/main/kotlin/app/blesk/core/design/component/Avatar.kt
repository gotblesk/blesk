package app.blesk.core.design.component

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.color.ensureContrast
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    /** A dot of the theme's light on the edge: the person is online right now. */
    online: Boolean = false,
    /** A photo file to show when no [photo] is given; read off the main thread. */
    photoFile: String? = null,
) {
    val loaded = rememberPhotoFile(photoFile.takeIf { photo == null })
    val picture = photo ?: loaded
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
            .let { if (online) it.onlineDot(size) else it }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (picture != null) {
            Image(
                bitmap = picture,
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

/**
 * A group without a photo: the monograms of two or three members ([faces] as name and colour
 * key) stuck together into one small drop, instead of the first letter of the group's name.
 */
@Composable
fun GroupAvatar(faces: List<Pair<String, String>>, modifier: Modifier = Modifier, size: Dp = Blesk.dimens.avatar) {
    val shown = faces.take(MAX_FACES)
    val face = size * if (shown.size == 2) PAIR_SHARE else TRIO_SHARE
    val spots = if (shown.size == 2) PairSpots else TrioSpots
    Box(
        modifier
            .size(size)
            .clearAndSetSemantics { }
            .metaballs(Blesk.colors.surfaceStrong, smoothness = size * BRIDGE_SHARE) {
                val r = face.toPx() / 2f + RIM.toPx()
                shown.indices.map { i ->
                    val c = Offset(this.size.width * spots[i].x, this.size.height * spots[i].y)
                    Blob(Rect(c.x - r, c.y - r, c.x + r, c.y + r), r)
                }
            },
    ) {
        shown.forEachIndexed { i, (name, key) ->
            Avatar(
                name = name,
                size = face,
                key = key,
                modifier = Modifier.offset(x = size * spots[i].x - face / 2, y = size * spots[i].y - face / 2),
            )
        }
    }
}

private val PairSpots = listOf(Offset(0.34f, 0.34f), Offset(0.66f, 0.66f))
private val TrioSpots = listOf(Offset(0.5f, 0.3f), Offset(0.3f, 0.68f), Offset(0.7f, 0.68f))
private const val MAX_FACES = 3
private const val PAIR_SHARE = 0.6f
private const val TRIO_SHARE = 0.5f
private const val BRIDGE_SHARE = 0.3f
private val RIM = 1.5.dp

/**
 * The «в сети» dot: 8 dp of the theme's light, filled, ringed with the background so it reads on
 * any avatar. In pale themes the light itself is almost white and would look like an empty ring,
 * so the dot takes the light's hue at a depth that stands out from the background.
 */
@Composable
private fun Modifier.onlineDot(size: Dp): Modifier {
    val colors = Blesk.colors
    val dot = remember(colors) {
        val light = Oklch.from(colors.light)
        val lightness = if (colors.isDark) maxOf(light.l, DOT_DARK_L) else DOT_LIGHT_L
        Oklch(lightness, maxOf(light.c, DOT_CHROMA), light.h).toColor()
    }
    val ring = colors.background
    return drawWithContent {
        drawContent()
        val r = OnlineDot.toPx() / 2f
        // On the rim at about half past four, where a dot sits naturally on a round face.
        val center = Offset(this.size.width * DOT_AT, this.size.height * DOT_AT)
        drawCircle(ring, r + DotRing.toPx(), center)
        drawCircle(dot, r, center)
    }.let { if (size < MIN_DOTTED) this else it }
}

private val OnlineDot = 8.dp
private val DotRing = 2.5.dp
private const val DOT_DARK_L = 0.8f
private const val DOT_LIGHT_L = 0.62f
private const val DOT_CHROMA = 0.14f
private val MIN_DOTTED = 20.dp
private const val DOT_AT = 0.85f

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

/** The hue, in degrees, that belongs to [key]: the one its monogram avatar is drawn in. */
fun avatarHue(key: String): Float = hueOf(key)

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

private const val HUE_STEPS = 8
private const val SPAN = 80f
private const val SLICES = 16
private const val RIM_SHARE = 0.045f

/** Decodes a small picture from [path] for an avatar, once per path; null until it is read or if it cannot be. */
@Composable
fun rememberPhotoFile(path: String?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(null, path) {
        value = path?.let {
            withContext(Dispatchers.IO) {
                runCatching {
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(it, bounds)
                    var sample = 1
                    while (bounds.outWidth / (sample * 2) >= AVATAR_PIXELS) sample *= 2
                    BitmapFactory.decodeFile(it, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
                }.getOrNull()
            }
        }
    }
    return bitmap
}

private const val AVATAR_PIXELS = 256
