package app.blesk.feature.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.HeaderArt
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk

/** The things the «Ты» header can do. */
class YouHeaderActions(val onEdit: () -> Unit, val onQr: () -> Unit, val onShare: () -> Unit)

/** A third of the screen, as the header of «Ты» is. */
@Composable
fun youHeaderHeight(): Dp = (LocalConfiguration.current.screenHeightDp / 3).dp

/**
 * The top of «Ты»: a header a third of the screen tall that melts into the background, the
 * avatar sitting on its lower edge, then the name and the action cluster. While scrolling the
 * header moves slower than the page and blurs, and the avatar shrinks on its way up into the
 * top lens.
 */
@Composable
fun YouHeader(
    profile: Profile,
    photo: ImageBitmap?,
    header: HeaderStyle,
    headerPhoto: ImageBitmap?,
    scroll: () -> Int,
    actions: YouHeaderActions,
    demo: Boolean,
    unnamed: String,
    labels: YouHeaderLabels,
) {
    val height = youHeaderHeight()
    val density = LocalDensity.current
    val heightPx = with(density) { height.toPx() }
    val key = profile.username.ifBlank { profile.name }
    val name = profile.name.ifBlank { unnamed }
    val reduced = Blesk.motion.reduced
    fun progress() = (scroll() / heightPx).coerceIn(0f, 1f)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(height + AvatarSize / 2)) {
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
                        val p = progress()
                        if (reduced) return@graphicsLayer
                        scaleX = lerp(1f, AVATAR_SHRINK, p)
                        scaleY = scaleX
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        alpha = 1f - ((p - FADE_FROM) / (1f - FADE_FROM)).coerceIn(0f, 1f)
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                Avatar(name, size = AvatarSize, photo = photo, protected = true, key = key)
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp, start = Blesk.dimens.gutter, end = Blesk.dimens.gutter),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = Blesk.type.title,
                maxLines = 2,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            if (demo) DemoTag()
        }
        if (profile.username.isNotBlank()) {
            Text("@${profile.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary)
        }
        if (profile.bio.isNotBlank()) {
            Text(
                text = profile.bio,
                color = Blesk.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter * 2).padding(top = 6.dp),
            )
        }
        ActionCluster(
            listOf(
                ClusterAction(BleskIcons.Edit, labels.edit, actions.onEdit),
                ClusterAction(BleskIcons.Qr, labels.qr, actions.onQr),
                ClusterAction(BleskIcons.Share, labels.share, actions.onShare),
            ),
            Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 16.dp),
        )
    }
}

/** Labels of the header, read from resources by the caller. */
class YouHeaderLabels(val edit: String, val qr: String, val share: String)

private class ClusterAction(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/**
 * Capsules stuck together into one drop. The one under the finger shrinks and comes away from
 * the others before its action runs.
 */
@Composable
private fun ActionCluster(actions: List<ClusterAction>, modifier: Modifier = Modifier) {
    val presses = actions.map { remember { MutableInteractionSource() } }
    val shrink = presses.map { source ->
        val pressed by source.collectIsPressedAsState()
        val value by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, Blesk.motion.press(), label = "press")
        value
    }
    val gap = 6.dp
    Row(
        modifier
            .fillMaxWidth()
            .metaballs(Blesk.colors.surface, smoothness = 12.dp) {
                val g = gap.toPx()
                val w = (size.width - g * (actions.size - 1)) / actions.size
                actions.indices.map { i ->
                    val s = shrink[i]
                    val cx = i * (w + g) + w / 2f
                    val half = w / 2f * s
                    val h = size.height * s
                    Blob(Rect(cx - half, (size.height - h) / 2f, cx + half, (size.height + h) / 2f), h / 2f)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        actions.forEachIndexed { index, action ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 64.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable(interactionSource = presses[index], indication = null, role = Role.Button, onClick = action.onClick)
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(action.icon, contentDescription = null, tint = Blesk.colors.textPrimary)
                Text(action.label, style = Blesk.type.caption, color = Blesk.colors.textPrimary, maxLines = 1)
            }
        }
    }
}

private val AvatarSize = 96.dp
private val ScrollBlur = 16.dp
private const val PARALLAX = 0.5f
private const val AVATAR_SHRINK = 0.35f
private const val FADE_FROM = 0.75f
private const val PRESSED_SCALE = 0.92f
