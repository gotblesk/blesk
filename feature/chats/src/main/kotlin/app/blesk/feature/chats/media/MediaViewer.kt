package app.blesk.feature.chats.media

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Slider
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.io.File
import app.blesk.core.design.R as DesignR

/** Full-screen photo (pinch to zoom) or video (tap to pause, slider to seek). */
@Composable
fun MediaViewer(attachment: Attachment, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black)) {
        when (attachment) {
            is Attachment.Photo -> ZoomablePhoto(attachment.path)
            is Attachment.Video -> VideoPlayer(attachment)
            else -> Unit
        }
        Box(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(8.dp)) {
            IconButton(BleskIcons.Back, stringResource(DesignR.string.design_back), onBack)
        }
    }
}

@Composable
private fun ZoomablePhoto(path: String) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    AsyncImage(
        model = File(path),
        contentDescription = stringResource(R.string.media_photo),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .transformable(state)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
}

private const val MAX_ZOOM = 5f

@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(video: Attachment.Video) {
    val context = LocalContext.current
    val player = remember(video.path) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.path))
            prepare()
            play()
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            delay(TICK_MILLIS)
        }
    }
    Box(Modifier.fillMaxSize()) {
        ContentFrame(
            player = player,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(if (playing) R.string.media_pause else R.string.media_play),
                ) { if (player.isPlaying) player.pause() else player.play() },
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .background(Blesk.colors.background.copy(alpha = 0.6f)),
        ) {
            Slider(
                title = formatDuration(position),
                value = position.toFloat(),
                range = 0f..video.durationMillis.coerceAtLeast(1).toFloat(),
                valueLabel = formatDuration(video.durationMillis),
                onValueChange = { player.seekTo(it.toLong()) },
            )
        }
    }
}

private const val TICK_MILLIS = 200L
