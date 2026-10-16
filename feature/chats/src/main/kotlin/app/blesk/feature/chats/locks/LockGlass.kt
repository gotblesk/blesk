package app.blesk.feature.chats.locks

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import kotlin.math.hypot

/**
 * What a locked chat looks like before it is opened: the chat itself under matte frost, and a lock
 * capsule in the middle. [thaw] runs from 0 (frozen) to 1 (clear): the frost melts from the centre
 * outwards, like a spoiler, and the capsule fades. Under the frost nothing can be read or tapped,
 * and TalkBack hears only the lock.
 */
@Composable
fun LockGlass(
    thaw: Float,
    dimmed: Boolean,
    onBiometric: (() -> Unit)?,
    onCode: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val frost = Blesk.colors.background
    val blurred = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    // Without a real blur the frost alone has to hide the text, so it is nearly opaque.
    val frostAlpha = if (blurred) FROST else FROST_SOLID
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .let { if (thaw < 1f) it.clearAndSetSemantics { } else it }
                .let { if (blurred && thaw < 1f) it.blur((BLUR_DP * (1f - thaw)).dp) else it },
        ) { content() }
        if (thaw >= 1f) return@Box
        Box(
            Modifier
                .fillMaxSize()
                .blockTaps()
                .drawWithContent {
                    val radius = hypot(size.width, size.height) / 2f * thaw
                    val hole = Path().apply { addOval(Rect(center, radius)) }
                    clipPath(hole, ClipOp.Difference) { drawRect(frost.copy(alpha = frostAlpha)) }
                    drawContent()
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier.graphicsLayer { alpha = (1f - thaw * 2f).coerceIn(0f, 1f) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LockCapsule(dimmed)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    onBiometric?.let {
                        Button(stringResource(R.string.lock_open_biometric), onClick = it, icon = BleskIcons.Fingerprint)
                    }
                    Button(
                        stringResource(R.string.lock_open_code),
                        onClick = onCode,
                        kind = if (onBiometric == null) ButtonKind.Primary else ButtonKind.Secondary,
                    )
                }
            }
        }
    }
}

/** The lock in a capsule; it dims for a moment when the way in was refused. */
@Composable
fun LockCapsule(dimmed: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier
            .graphicsLayer { alpha = if (dimmed) DIMMED else 1f }
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(BleskIcons.Lock, contentDescription = null, size = 18.dp)
        Text(stringResource(R.string.lock_closed), style = Blesk.type.bodyStrong)
    }
}

/** The small lock that sticks to the edge of a locked chat's avatar in the list. */
@Composable
fun AvatarLock(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(20.dp)
            .background(Blesk.colors.surfaceStrong, CircleShape)
            .padding(3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BleskIcons.Lock, contentDescription = null, size = 12.dp, tint = Blesk.colors.textPrimary)
    }
}

/** «Скрыто» in a matte capsule, in place of a locked chat's last message. */
@Composable
fun HiddenPreview(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.lock_hidden),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = modifier
            .background(Blesk.colors.surface, CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

private const val BLUR_DP = 24f
private const val FROST = 0.55f
private const val FROST_SOLID = 0.97f
private const val DIMMED = 0.4f
