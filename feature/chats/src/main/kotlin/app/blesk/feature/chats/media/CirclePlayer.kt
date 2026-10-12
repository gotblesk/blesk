package app.blesk.feature.chats.media

import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File

/**
 * A round video. Once it has arrived it plays by itself without sound, over and over, as long as
 * it is on screen; a tap starts it again from the beginning with sound and a ring of progress, and
 * another tap goes back to silence. Without motion, or veiled in a request, it waits for the tap.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CirclePlayer(circle: Attachment.Circle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var sound by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val autoplay = !Blesk.motion.reduced && !LocalVeiledMedia.current
    val ring = Blesk.colors.light
    val action = stringResource(if (sound) R.string.media_circle_mute else R.string.media_circle_sound)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = action) { sound = !sound },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(circle.poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().veiled(),
        )
        if (circle.path.isNotEmpty() && (sound || autoplay)) {
            val player = remember(circle.path) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(circle.path))
                    prepare()
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) sound = false
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            // Silent playback must not take the audio focus from music or a call.
            LaunchedEffect(player, sound) {
                player.setAudioAttributes(AudioAttributes.DEFAULT, sound)
                player.volume = if (sound) 1f else 0f
                player.repeatMode = if (sound) Player.REPEAT_MODE_OFF else Player.REPEAT_MODE_ONE
                player.seekTo(0)
                player.play()
                progress = 0f
                while (sound) {
                    progress = player.currentPosition.toFloat() / circle.durationMillis.coerceAtLeast(1)
                    delay(PROGRESS_TICK_MILLIS)
                }
            }
            ContentFrame(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Crop,
                keepContentOnReset = true,
                shutter = {},
                modifier = Modifier.fillMaxSize(),
            )
            if (sound) {
                Canvas(Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    drawArc(
                        color = ring,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(stroke / 2f, stroke / 2f),
                        size = Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

private const val PROGRESS_TICK_MILLIS = 50L
