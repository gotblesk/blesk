package app.blesk.feature.chats.media

import android.content.pm.ActivityInfo
import android.util.Rational
import androidx.activity.compose.LocalActivity
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import kotlinx.coroutines.delay

/**
 * A video with blesk's own controls in the bottom lens, which fade after three seconds of
 * playing. A tap shows or hides them; a double tap on either half jumps ten seconds. The line
 * of light shows what was watched, what has come down and what has not.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VideoPlayer(video: Attachment.Video, load: ExoPlayer.() -> Unit) {
    val context = LocalContext.current
    val pip = LocalPictureInPicture.current
    val inCorner = pip?.active?.value == true
    val player = remember(video) {
        ExoPlayer.Builder(context).build().apply {
            load()
            prepare()
            play()
        }
    }
    var position by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var shown by remember { mutableStateOf(true) }
    var touched by remember { mutableIntStateOf(0) }
    var skip by remember { mutableStateOf<Int?>(null) }
    val duration = video.durationMillis.coerceAtLeast(1)

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
            pip?.onVideo(null)
        }
    }
    // Only a video that is playing goes into the corner when the person leaves.
    LaunchedEffect(playing) {
        pip?.onVideo(if (playing) aspectOf(video) else null)
    }
    // Closing the corner window stops the activity: the sound must not go on alone.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { player.pause() }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            buffered = player.bufferedPosition
            delay(TICK_MILLIS)
        }
    }
    LaunchedEffect(shown, playing, touched) {
        if (shown && playing) {
            delay(HIDE_AFTER_MILLIS)
            shown = false
        }
    }
    LaunchedEffect(skip) {
        if (skip != null) {
            delay(SKIP_SHOWN_MILLIS)
            skip = null
        }
    }

    fun jump(seconds: Int) {
        player.seekTo((player.currentPosition + seconds * MILLIS_PER_SECOND).coerceIn(0, duration))
        skip = seconds
    }

    val back = stringResource(R.string.video_back_10)
    val forward = stringResource(R.string.video_forward_10)
    Box(Modifier.fillMaxSize()) {
        ContentFrame(
            player = player,
            contentScale = ContentScale.Fit,
            // No black shutter: the poster underneath shows until the first frame.
            shutter = {},
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    customActions = listOf(
                        CustomAccessibilityAction(back) { jump(-SKIP_SECONDS); true },
                        CustomAccessibilityAction(forward) { jump(SKIP_SECONDS); true },
                    )
                }
                .pointerInput(player) {
                    detectTapGestures(
                        onTap = { shown = !shown },
                        onDoubleTap = { at -> jump(if (at.x < size.width / 2f) -SKIP_SECONDS else SKIP_SECONDS) },
                    )
                },
        )
        skip?.let { seconds ->
            Text(
                text = stringResource(if (seconds < 0) R.string.video_skipped_back else R.string.video_skipped_forward, SKIP_SECONDS),
                style = Blesk.type.bodyStrong,
                color = Color.White,
                modifier = Modifier
                    .align(if (seconds < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = SCRIM))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AnimatedVisibility(
            visible = (shown || !playing) && !inCorner,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomLens {
                LightBar(
                    position = position,
                    buffered = buffered,
                    duration = duration,
                    onSeek = { millis ->
                        player.seekTo(millis)
                        touched++
                    },
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        if (playing) BleskIcons.Pause else BleskIcons.Play,
                        stringResource(if (playing) R.string.media_pause else R.string.media_play),
                        {
                            if (player.isPlaying) player.pause() else player.play()
                            touched++
                        },
                    )
                    Text(
                        "${formatDuration(position)} / ${formatDuration(duration)}",
                        style = Blesk.type.label,
                        modifier = Modifier.weight(1f).padding(start = 4.dp),
                    )
                    SpeedButton(speed) {
                        speed = nextSpeed(speed)
                        player.setPlaybackSpeed(speed)
                        touched++
                    }
                    FullscreenButton(video) { touched++ }
                }
            }
        }
    }
}

@Composable
private fun SpeedButton(speed: Float, onClick: () -> Unit) {
    val label = stringResource(R.string.video_speed, formatSpeed(speed))
    Box(
        Modifier
            .heightIn(min = 48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(formatSpeed(speed), style = Blesk.type.bodyStrong)
    }
}

/** Turns the phone's picture sideways for a wide video, and back; the turn is undone on leaving. */
@Composable
private fun FullscreenButton(video: Attachment.Video, onUse: () -> Unit) {
    val activity = LocalActivity.current ?: return
    var full by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        onDispose { activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    val wide = video.width >= video.height
    IconButton(
        if (full) BleskIcons.Shrink else BleskIcons.Expand,
        stringResource(if (full) R.string.video_fullscreen_exit else R.string.video_fullscreen),
        {
            full = !full
            activity.requestedOrientation = when {
                !full -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                wide -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
            onUse()
        },
    )
}

/**
 * The progress line, drawn as light: what was watched glows in the theme's light, what has come
 * down is matte, the rest is faint. Under the finger while seeking, a spark.
 */
@Composable
private fun LightBar(position: Long, buffered: Long, duration: Long, onSeek: (Long) -> Unit) {
    val light = Blesk.colors.light
    val matte = Blesk.colors.textSecondary.copy(alpha = MATTE_ALPHA)
    val faint = Blesk.colors.textTertiary.copy(alpha = FAINT_ALPHA)
    var scrub by remember { mutableStateOf<Float?>(null) }
    val label = stringResource(R.string.video_seek)
    val played = scrub ?: (position.toFloat() / duration).coerceIn(0f, 1f)
    val loaded = (buffered.toFloat() / duration).coerceIn(0f, 1f)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(BarHeight)
            .padding(horizontal = 16.dp)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(played, 0f..1f)
                setProgress { target ->
                    onSeek((target.coerceIn(0f, 1f) * duration).toLong())
                    true
                }
            }
            .pointerInput(duration) {
                detectTapGestures { at -> onSeek((at.x / size.width * duration).toLong().coerceIn(0, duration)) }
            }
            .pointerInput(duration) {
                detectHorizontalDragGestures(
                    onDragStart = { at -> scrub = (at.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        scrub?.let { onSeek((it * duration).toLong()) }
                        scrub = null
                    },
                    onDragCancel = { scrub = null },
                ) { change, _ -> scrub = (change.position.x / size.width).coerceIn(0f, 1f) }
            },
    ) {
        val y = size.height / 2f
        val stroke = LineWidth.toPx()
        drawLine(faint, Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
        drawLine(matte, Offset(0f, y), Offset(size.width * loaded, y), stroke, StrokeCap.Round)
        val end = Offset(size.width * played, y)
        drawLine(light.copy(alpha = GLOW_ALPHA), Offset(0f, y), end, stroke * GLOW_WIDTH, StrokeCap.Round)
        drawLine(light, Offset(0f, y), end, stroke, StrokeCap.Round)
        val radius = if (scrub != null) SparkRadius.toPx() else stroke * 1.5f
        drawCircle(Brush.radialGradient(listOf(light, light.copy(alpha = 0f)), end, radius), radius, end)
        drawCircle(Color.White, stroke, end)
    }
}

private fun aspectOf(video: Attachment.Video): Rational? {
    if (video.width <= 0 || video.height <= 0) return null
    // The system refuses corner windows narrower or wider than 2.39:1.
    val ratio = (video.width.toFloat() / video.height).coerceIn(1 / MAX_ASPECT, MAX_ASPECT)
    return Rational((ratio * ASPECT_SCALE).toInt(), ASPECT_SCALE)
}

private val Speeds = listOf(1f, 1.25f, 1.5f, 2f, 0.5f)

private fun nextSpeed(current: Float) = Speeds[(Speeds.indexOf(current) + 1) % Speeds.size]

private fun formatSpeed(speed: Float) = (if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()) + "×"

private val BarHeight = 32.dp
private val LineWidth = 3.dp
private val SparkRadius = 14.dp
private const val GLOW_WIDTH = 3f
private const val GLOW_ALPHA = 0.3f
private const val MATTE_ALPHA = 0.6f
private const val FAINT_ALPHA = 0.35f
private const val SCRIM = 0.45f
private const val TICK_MILLIS = 200L
private const val HIDE_AFTER_MILLIS = 3_000L
private const val SKIP_SHOWN_MILLIS = 700L
private const val SKIP_SECONDS = 10
private const val MILLIS_PER_SECOND = 1_000L
private const val MAX_ASPECT = 2.39f
private const val ASPECT_SCALE = 1000
