package app.blesk.feature.chats.media

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Transfer
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R

/** Files on their way, by message id or `id/index` for album items; provided once per conversation. */
val LocalTransfers = compositionLocalOf<Map<String, Transfer>> { emptyMap() }

/**
 * A ring of the theme's light filling as bytes go: tapping it cancels, and after a failure it
 * offers to try again from where it stopped. Under it, «12,4 из 48 МБ»; for files over 10 MB also
 * the speed and how long is left.
 */
@Composable
internal fun TransferRing(transfer: Transfer, onCancel: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val light = Blesk.colors.light
    val track = Color.White.copy(alpha = TRACK_ALPHA)
    val action = stringResource(if (transfer.failed) R.string.media_retry else R.string.media_transfer_cancel)
    val speed = rememberSpeed(transfer.done)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(RingSize)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = SCRIM))
                .clickable(role = Role.Button, onClickLabel = action) { if (transfer.failed) onRetry() else onCancel() }
                .semantics { contentDescription = action },
            contentAlignment = Alignment.Center,
        ) {
            if (!transfer.failed) {
                Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                    val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(track, 0f, FULL_TURN, useCenter = false, style = stroke)
                    drawArc(light, START_ANGLE, FULL_TURN * transfer.fraction, useCenter = false, style = stroke)
                }
            }
            Icon(
                icon = if (transfer.failed) BleskIcons.Download else BleskIcons.Close,
                contentDescription = null,
                size = 20.dp,
                tint = Color.White,
            )
        }
        if (transfer.total > 0) {
            val sizes = stringResource(R.string.media_transfer, formatSize(transfer.done), formatSize(transfer.total))
            val left = if (transfer.total > BIG_FILE && speed > 0f) {
                val seconds = ((transfer.total - transfer.done) / speed).toLong()
                stringResource(R.string.media_transfer_speed, formatSize(speed.toLong()), formatLeft(seconds))
            } else {
                null
            }
            Text(
                text = listOfNotNull(sizes, left).joinToString(" · "),
                style = Blesk.type.caption,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = SCRIM))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/** Bytes per second, smoothed over the last few updates. */
@Composable
private fun rememberSpeed(done: Long): Float {
    val sample = remember { Sample() }
    val now = System.nanoTime()
    if (sample.at == 0L) {
        sample.at = now
        sample.done = done
    } else if (now - sample.at > SAMPLE_NANOS) {
        val instant = (done - sample.done) * NANOS_PER_SECOND / (now - sample.at).toFloat()
        sample.speed = if (sample.speed == 0f) instant else sample.speed * (1 - SMOOTHING) + instant * SMOOTHING
        sample.at = now
        sample.done = done
    }
    return sample.speed
}

/** Plain fields: the speed follows the progress it is read with and never causes a redraw itself. */
private class Sample {
    var at = 0L
    var done = 0L
    var speed = 0f
}

@Composable
private fun formatLeft(seconds: Long): String = when {
    seconds < SECONDS_PER_MINUTE -> stringResource(R.string.media_left_seconds, seconds.coerceAtLeast(1))
    else -> stringResource(R.string.media_left_minutes, (seconds + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE)
}

private val RingSize = 48.dp
private const val SCRIM = 0.45f
private const val TRACK_ALPHA = 0.25f
private const val FULL_TURN = 360f
private const val START_ANGLE = -90f
private const val BIG_FILE = 10L * 1024 * 1024
private const val SAMPLE_NANOS = 500_000_000L
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val SMOOTHING = 0.3f
private const val SECONDS_PER_MINUTE = 60L
