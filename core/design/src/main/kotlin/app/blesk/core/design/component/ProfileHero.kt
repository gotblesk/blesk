package app.blesk.core.design.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.design.theme.Blesk

/** A third of the screen, as tall as a profile header is. */
@Composable
fun profileHeaderHeight(): Dp = (LocalConfiguration.current.screenHeightDp / 3).dp

/**
 * The top of a profile, yours or someone's: a header a third of the screen tall that melts into
 * the background, with the avatar sitting on its lower edge. While the page scrolls ([scroll]
 * is how far, in pixels) the header moves slower and blurs, and the avatar shrinks on its way up
 * into the top lens. [protected] puts the white glint of encryption on the avatar's rim.
 */
@Composable
fun ProfileHero(
    name: String,
    key: String,
    photo: ImageBitmap?,
    header: HeaderStyle,
    headerPhoto: ImageBitmap?,
    scroll: () -> Int,
    modifier: Modifier = Modifier,
    protected: Boolean = true,
) {
    val height = profileHeaderHeight()
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val reduced = Blesk.motion.reduced
    fun progress() = (scroll() / heightPx).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height + AvatarSize / 2)) {
        HeaderArt(
            style = header,
            photo = headerPhoto,
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { if (!reduced) translationY = scroll() * PARALLAX }
                .blur(if (reduced) 0.dp else ScrollBlur * progress()),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    if (reduced) return@graphicsLayer
                    val p = progress()
                    scaleX = lerp(1f, AVATAR_SHRINK, p)
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = 1f - ((p - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f)
                },
        ) {
            Avatar(name, size = AvatarSize, photo = photo, protected = protected, key = key)
        }
    }
}

private val AvatarSize = 96.dp
private val ScrollBlur = 16.dp
private const val PARALLAX = 0.5f
private const val AVATAR_SHRINK = 0.35f
private const val FADE_FROM = 0.75f
