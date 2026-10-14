package app.blesk.core.design.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.design.R
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.glass.lens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import kotlin.math.PI
import kotlin.math.sin

/** A third of the screen, as tall as a profile header is. */
@Composable
fun profileHeaderHeight(): Dp = (LocalConfiguration.current.screenHeightDp / 3).dp

/** How far before the end of the header the top lens gathers, the avatar having risen into it. */
val ProfileLensReach = 72.dp

/**
 * The big name under a profile's avatar flows into the top lens: it fades over the last stretch
 * before the lens gathers and is gone once the lens holds the name, so the two are never seen at
 * once. Read while drawing, so scrolling does not compose the name again.
 */
@Composable
fun Modifier.nameIntoLens(scroll: () -> Int): Modifier {
    val header = profileHeaderHeight()
    return graphicsLayer {
        val gatherAt = header.toPx() - ProfileLensReach.toPx()
        val fade = NameFade.toPx()
        alpha = 1f - ((scroll() - (gatherAt - fade)) / fade).coerceIn(0f, 1f)
    }
}

private val NameFade = 40.dp

/**
 * The top of a profile page: over the header only a glass «назад»; once the avatar has flowed
 * up ([scroll] past the header, less [ProfileLensReach]), the whole glass lens with [title],
 * coming in as the big name goes. [actions] sit in the lens once it has gathered. With [done],
 * the way out is that word on a capsule — «Готово» while editing — instead of the arrow.
 */
@Composable
fun ProfileTopLens(
    title: String,
    scroll: () -> Int,
    onBack: () -> Unit,
    done: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val gatherAt = with(LocalDensity.current) { (profileHeaderHeight() - ProfileLensReach).toPx() }
    val gathered by remember(gatherAt) { derivedStateOf { scroll() > gatherAt } }
    val way: @Composable () -> Unit = {
        if (done != null) {
            Text(
                text = done,
                style = Blesk.type.bodyStrong,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
            )
        } else {
            IconButton(BleskIcons.Back, stringResource(R.string.design_back), onBack)
        }
    }
    Box {
        AnimatedVisibility(visible = gathered, enter = fadeIn(Blesk.motion.press()), exit = fadeOut(Blesk.motion.press())) {
            TopLens(title = title, onBack = if (done == null) onBack else null, leading = { if (done != null) way() }, actions = actions)
        }
        if (!gathered) {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 12.dp)) {
                Box(Modifier.lens(CircleShape)) { way() }
            }
        }
    }
}

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
    /** While the profile is being edited, a tap on the avatar picks a new photo. */
    onAvatarClick: (() -> Unit)? = null,
    avatarClickLabel: String? = null,
) {
    val height = profileHeaderHeight()
    val heightPx = with(LocalDensity.current) { height.toPx() }
    val reduced = Blesk.motion.reduced
    fun progress() = (scroll() / heightPx).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().height(height + AvatarSize / 2 + NeckRim)) {
        HeaderArt(
            style = header,
            photo = headerPhoto,
            hue = avatarHue(key),
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .graphicsLayer { if (!reduced) translationY = scroll() * PARALLAX }
                .blur(if (reduced) 0.dp else ScrollBlur * progress()),
        )
        // The neck is the header's own light where the header is «Свет», so the avatar hangs from it.
        val neck = if (header is HeaderStyle.Light) headerLight(avatarHue(key), Blesk.colors.isDark) else Blesk.colors.surfaceStrong
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
            // The avatar hangs from the header on a short neck of liquid: a thin rim around it narrows
            // upwards into a drop that runs into the header.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .size(width = AvatarSize + NeckRim * 2, height = AvatarSize + NeckRim + NeckReach)
                    // Only in motion: at rest the avatar simply sits on the edge; while the page scrolls and
                    // the avatar flows up into the lens the neck draws out, and it is gone again once inside.
                    .graphicsLayer { alpha = if (reduced) 0f else NECK_ALPHA * sin(PI.toFloat() * progress()) }
                    .metaballs(neck, smoothness = NeckSmoothness) {
                        val w = size.width
                        val ring = AvatarSize.toPx() / 2f + NeckRim.toPx()
                        val neckHalf = w * NECK_WIDTH / 2f
                        listOf(
                            Blob(Rect(w / 2f - ring, size.height - 2f * ring, w / 2f + ring, size.height), ring),
                            Blob(Rect(w / 2f - neckHalf, 0f, w / 2f + neckHalf, NeckReach.toPx() * 2f), neckHalf),
                        )
                    },
            )
            Box(Modifier.align(Alignment.BottomCenter).padding(bottom = NeckRim)) {
                Avatar(
                    name, size = AvatarSize, photo = photo, protected = protected, key = key,
                    modifier = if (onAvatarClick == null) {
                        Modifier
                    } else {
                        Modifier.clip(CircleShape).clickable(role = Role.Button, onClickLabel = avatarClickLabel, onClick = onAvatarClick)
                    },
                )
                if (onAvatarClick != null) {
                    Icon(
                        icon = BleskIcons.Camera,
                        contentDescription = null,
                        tint = Blesk.colors.onAccent,
                        size = 16.dp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(BadgeSize)
                            .clip(CircleShape)
                            .background(Blesk.colors.accent)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}

private val AvatarSize = 96.dp
private val BadgeSize = 28.dp
private val ScrollBlur = 16.dp
private val NeckRim = 2.dp
private val NeckReach = 40.dp
private val NeckSmoothness = 20.dp
private const val NECK_WIDTH = 0.26f
private const val NECK_ALPHA = 0.6f
private const val PARALLAX = 0.5f
private const val AVATAR_SHRINK = 0.35f
private const val FADE_FROM = 0.75f
