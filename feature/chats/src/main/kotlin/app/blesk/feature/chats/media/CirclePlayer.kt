package app.blesk.feature.chats.media

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.fire.breathing
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.LoudnessMeter
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * A round video. Once it has arrived it plays by itself without sound, over and over, as long as
 * it is on screen. A tap pours it out into a big circle in the middle of the screen, with sound
 * (see [OpenCircle]). Without motion, or veiled in a request, it waits for the tap.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CirclePlayer(circle: Attachment.Circle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val autoplay = Blesk.motion.autoplay && !Blesk.motion.reduced && !LocalVeiledMedia.current && !open
    val action = stringResource(R.string.media_circle_open)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = action, enabled = circle.path.isNotEmpty()) { open = true },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = File(circle.poster),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().veiled(),
        )
        if (circle.path.isNotEmpty() && autoplay) {
            val player = remember(circle.path) {
                ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(circle.path))
                    // Silent playback must not take the audio focus from music or a call.
                    setAudioAttributes(AudioAttributes.DEFAULT, false)
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                    play()
                }
            }
            DisposableEffect(player) { onDispose { player.release() } }
            ContentFrame(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Crop,
                keepContentOnReset = true,
                shutter = {},
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
    if (open) OpenCircle(circle, onClose = { open = false })
}

/**
 * The circle poured out into the middle of the screen, about 80 % of its width, with sound and
 * everything else dimmed. Its edge breathes with the voice and glows as far as it has played; a
 * slide along the edge seeks. A tap, or a swipe down, puts it back in its place.
 */
@OptIn(UnstableApi::class)
@Composable
private fun OpenCircle(circle: Attachment.Circle, onClose: () -> Unit) {
    val context = LocalContext.current
    val controller = LocalMediaController.current
    val speed by controller.voiceSpeed
    val meter = remember { LoudnessMeter() }
    val level by meter.level.collectAsStateWithLifecycle()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val grow = remember { Animatable(GROW_FROM) }
    val drop = remember { Animatable(0f) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember(circle.path) {
        ExoPlayer.Builder(context, meter.renderers(context)).build().apply {
            setMediaItem(MediaItem.fromUri(circle.path))
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            prepare()
            play()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(speed) { player.playbackParameters = PlaybackParameters(speed) }
    val motion = Blesk.motion
    LaunchedEffect(Unit) { grow.animateTo(1f, motion.stretch()) }
    LaunchedEffect(player) {
        while (true) {
            progress = (player.currentPosition.toFloat() / circle.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f)
            delay(PROGRESS_TICK_MILLIS)
        }
    }
    val ring = Blesk.colors.light
    val track = Color.White.copy(alpha = TRACK_ALPHA)
    val dropLimit = with(LocalDensity.current) { DropDistance.toPx() }
    val closeLabel = stringResource(R.string.media_circle_close)
    val seekLabel = stringResource(R.string.media_circle_seek)
    fun seek(fraction: Float) = player.seekTo((circle.durationMillis * fraction.coerceIn(0f, 1f)).toLong())
    Popup(onDismissRequest = onClose, properties = PopupProperties(focusable = true, clippingEnabled = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = SCRIM * (1f - drop.value / (dropLimit * 2f)).coerceIn(0f, 1f)))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = closeLabel,
                    onClick = onClose,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth(OPEN_SHARE)
                        .aspectRatio(1f)
                        .graphicsLayer {
                            scaleX = grow.value
                            scaleY = grow.value
                            translationY = drop.value
                        }
                        .breathing(level)
                        .semantics {
                            contentDescription = seekLabel
                            progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                            setProgress { value -> seek(value); true }
                        }
                        .pointerInput(circle.path) {
                            var onRing = false
                            var lastTenth = -1
                            detectDragGestures(
                                onDragStart = { start ->
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val distance = hypot(start.x - center.x, start.y - center.y)
                                    onRing = distance > size.width / 2f - RingReach.toPx()
                                },
                                onDragEnd = {
                                    if (!onRing && drop.value > dropLimit) onClose() else scope.launch { drop.animateTo(0f) }
                                },
                            ) { change, amount ->
                                change.consume()
                                if (onRing) {
                                    val center = Offset(size.width / 2f, size.height / 2f)
                                    val angle = atan2(change.position.y - center.y, change.position.x - center.x)
                                    // Clockwise from the top, like the light on the edge.
                                    val fraction = (((angle + PI / 2) / (2 * PI)).toFloat() + 1f) % 1f
                                    val tenth = (fraction * TENTHS).toInt()
                                    if (tenth != lastTenth) {
                                        lastTenth = tenth
                                        haptics.tick()
                                    }
                                    seek(fraction)
                                    progress = fraction
                                } else {
                                    scope.launch { drop.snapTo((drop.value + amount.y).coerceAtLeast(0f)) }
                                }
                            }
                        }
                        .clip(CircleShape)
                        .clickable(onClickLabel = closeLabel, onClick = onClose),
                ) {
                    ContentFrame(
                        player = player,
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = ContentScale.Crop,
                        keepContentOnReset = true,
                        shutter = {},
                        modifier = Modifier.fillMaxSize(),
                    )
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = RingWidth.toPx()
                        val arc = Size(size.width - stroke, size.height - stroke)
                        val topLeft = Offset(stroke / 2f, stroke / 2f)
                        drawArc(track, -90f, 360f, false, topLeft, arc, style = Stroke(stroke))
                        drawArc(ring, -90f, 360f * progress, false, topLeft, arc, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
                SpeedCapsule(speed, controller.onCycleSpeed, controller.onSetSpeed)
            }
        }
    }
}

private const val PROGRESS_TICK_MILLIS = 50L
private const val OPEN_SHARE = 0.8f
private const val GROW_FROM = 0.4f
private const val SCRIM = 0.7f
private const val TRACK_ALPHA = 0.25f
private const val TENTHS = 10
private val RingWidth = 4.dp
private val RingReach = 36.dp
private val DropDistance = 120.dp
