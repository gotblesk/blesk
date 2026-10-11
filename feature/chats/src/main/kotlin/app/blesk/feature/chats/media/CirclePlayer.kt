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

/** A round video: the poster until tapped, then it plays inside the circle with a ring of progress. */
@OptIn(UnstableApi::class)
@Composable
internal fun CirclePlayer(circle: Attachment.Circle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var playing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val ring = Blesk.colors.light
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(if (playing) R.string.media_pause else R.string.media_play)) {
                playing = !playing
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(circle.poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (playing) {
            val player = remember(circle.path) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(circle.path))
                    prepare()
                    play()
                }
            }
            DisposableEffect(player) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) playing = false
                    }
                }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                    progress = 0f
                }
            }
            LaunchedEffect(player) {
                while (true) {
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

private const val PROGRESS_TICK_MILLIS = 50L
