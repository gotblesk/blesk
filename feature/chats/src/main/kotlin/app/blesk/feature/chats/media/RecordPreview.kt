package app.blesk.feature.chats.media

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import app.blesk.core.data.model.Attachment
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Clip
import app.blesk.core.media.MediaTrimmer
import app.blesk.core.media.Piece
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * «Отправить» while recording and in the preview: a filled capsule in the theme's light with
 * its icon, so it cannot be mistaken for one more arrow among the buttons.
 */
@Composable
internal fun SendCapsule(onSend: () -> Unit, modifier: Modifier = Modifier, label: Boolean = false) {
    val dark = Blesk.colors.isDark
    val fill = if (dark) Blesk.colors.light else Blesk.colors.accent
    val ink = if (dark) Blesk.colors.background else Blesk.colors.onAccent
    val text = stringResource(R.string.media_stop_send)
    Row(
        modifier
            .height(SendHeight)
            .clip(CircleShape)
            .background(fill)
            .clickable(role = Role.Button, onClickLabel = text, onClick = onSend)
            .semantics { contentDescription = text }
            .padding(horizontal = if (label) 18.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(BleskIcons.Send, contentDescription = null, tint = ink, size = 20.dp)
        if (label) Text(stringResource(R.string.media_send), style = Blesk.type.bodyStrong, color = ink)
    }
}

/**
 * A voice note stopped with «Стоп», in the composer: play it, pull the handles at the ends of its
 * wave to cut the start or the end, «Дописать» to record on, «Удалить» or «Отправить».
 */
@OptIn(UnstableApi::class)
@Composable
internal fun VoicePreview(draft: RecordDraft.Voice, ui: RecordingUi, recorders: Recorders, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    var from by remember(draft) { mutableFloatStateOf(0f) }
    var to by remember(draft) { mutableFloatStateOf(1f) }
    var playing by remember(draft) { mutableStateOf(false) }
    var position by remember(draft) { mutableFloatStateOf(0f) }
    val bars = remember(draft) { draftBars(draft.pieces) }
    val total = draft.durationMillis
    val player = remember(draft) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) playing = false
                }
            })
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    var clips by remember(draft) { mutableStateOf(emptyList<Clip>()) }

    // Plays only what is kept: each piece cut to its share of the selection.
    fun play() {
        clips = MediaTrimmer.clips(draft.pieces.map { Piece(it.path, it.durationMillis) }, (from * total).toLong(), (to * total).toLong())
        player.setMediaItems(clips.map(::clipItem))
        player.prepare()
        player.play()
        playing = true
    }
    LaunchedEffect(playing) {
        while (playing) {
            val heardBefore = clips.take(player.currentMediaItemIndex).sumOf { it.endMillis - it.startMillis }
            position = ((from * total + heardBefore + player.currentPosition) / total.coerceAtLeast(1)).coerceIn(0f, 1f)
            delay(PROGRESS_MILLIS)
        }
    }
    val kept = ((to - from) * total).toLong()
    Row(
        modifier.fillMaxWidth().height(PreviewHeight).padding(start = 4.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(BleskIcons.Trash, stringResource(R.string.media_delete_recording), { discard(ui) })
        val action = stringResource(if (playing) R.string.media_pause else R.string.media_play)
        Icon(
            icon = if (playing) BleskIcons.Pause else BleskIcons.Play,
            contentDescription = action,
            tint = Blesk.colors.onAccent,
            size = 18.dp,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Blesk.colors.accent)
                .clickable(role = Role.Button, onClickLabel = action) {
                    if (playing) {
                        player.pause()
                        playing = false
                    } else {
                        play()
                    }
                }
                .padding(9.dp),
        )
        TrimWave(
            bars = bars,
            from = from,
            to = to,
            position = if (playing) position else -1f,
            onChange = { start, end ->
                from = start
                to = end
                if (playing) {
                    player.pause()
                    playing = false
                }
                haptics.tick()
            },
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(kept),
            style = Blesk.type.caption,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(start = 6.dp),
        )
        IconButton(BleskIcons.Mic, stringResource(R.string.media_record_more), {
            player.pause()
            ui.mode = RecordMode.Voice
            if (begin(ui, recorders, owner, scope)) {
                ui.locked = true
                recorders.onLock()
                haptics.tick()
            }
        })
        SendCapsule(onSend = {
            player.pause()
            ui.draft = null
            sendVoice(draft.pieces, from..to, ui, recorders, scope)
            haptics.success()
        })
    }
}

/**
 * The wave of a recording with a handle at each end: drag one in to cut the start or the end. What
 * is cut stays faint; while playing, the heard part shines.
 */
@Composable
private fun TrimWave(
    bars: FloatArray,
    from: Float,
    to: Float,
    position: Float,
    onChange: (from: Float, to: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kept = Blesk.colors.textPrimary
    val cut = Blesk.colors.textPrimary.copy(alpha = CUT_ALPHA)
    val lit = if (Blesk.colors.isDark) Blesk.colors.light else Blesk.colors.accent
    val handle = Blesk.colors.accent
    val label = stringResource(R.string.media_trim)
    var dragging by remember { mutableStateOf<Int?>(null) }
    Canvas(
        modifier
            .height(36.dp)
            .semantics { contentDescription = label }
            .pointerInput(from, to) {
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        val f = at.x / size.width
                        dragging = if (abs(f - from) <= abs(f - to)) 0 else 1
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    when (dragging) {
                        0 -> onChange(f.coerceAtMost(to - MIN_KEPT), to)
                        1 -> onChange(from, f.coerceAtLeast(from + MIN_KEPT))
                    }
                }
            },
    ) {
        val step = size.width / bars.size.coerceAtLeast(1)
        val width = (step * BAR_SHARE).coerceAtMost(3.dp.toPx())
        bars.forEachIndexed { i, value ->
            val x = step * i + step / 2f
            val f = x / size.width
            val half = (3.dp.toPx() + (size.height - 3.dp.toPx()) * value.coerceIn(0f, 1f)) / 2f
            val color = when {
                f < from || f > to -> cut
                position in 0f..1f && f <= position -> lit
                else -> kept
            }
            drawLine(color, Offset(x, size.height / 2f - half), Offset(x, size.height / 2f + half), width, StrokeCap.Round)
        }
        listOf(from, to).forEach { at ->
            val x = (at * size.width).coerceIn(2.dp.toPx(), size.width - 2.dp.toPx())
            drawRoundRect(
                color = handle,
                topLeft = Offset(x - HandleWidth.toPx() / 2f, 0f),
                size = Size(HandleWidth.toPx(), size.height),
                cornerRadius = CornerRadius(HandleWidth.toPx() / 2f),
            )
        }
    }
}

/**
 * A round video stopped with «Стоп»: it plays round and round with sound, a strip of its frames
 * with handles cuts the start or the end; «Переснять», «Удалить», and «Отправить» right by the circle.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun CirclePreview(draft: RecordDraft.Circle, ui: RecordingUi, recorders: Recorders) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val circle = draft.circle
    var from by remember(draft) { mutableFloatStateOf(0f) }
    var to by remember(draft) { mutableFloatStateOf(1f) }
    val player = remember(draft) {
        ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            repeatMode = Player.REPEAT_MODE_ONE
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(player, from, to) {
        player.setMediaItem(clipItem(Clip(circle.path, (from * circle.durationMillis).toLong(), (to * circle.durationMillis).toLong())))
        player.prepare()
        player.play()
    }
    val frames by produceState<List<ImageBitmap>>(emptyList(), circle.path) { value = withContext(Dispatchers.IO) { framesOf(circle) } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box {
            Box(Modifier.size(CircleSize).clip(CircleShape).background(Blesk.colors.surface)) {
                ContentFrame(
                    player = player,
                    surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                    contentScale = ContentScale.Crop,
                    keepContentOnReset = true,
                    shutter = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
            // «Отправить» stands right by the circle, not only at the bottom of the screen.
            SendCapsule(
                onSend = {
                    player.pause()
                    ui.draft = null
                    sendCircle(circle, from..to, ui, recorders, scope)
                    haptics.success()
                },
                label = true,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        FrameStrip(
            frames = frames,
            from = from,
            to = to,
            onChange = { start, end ->
                from = start
                to = end
                haptics.tick()
            },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clip(CircleShape).background(Blesk.colors.surface)) {
                IconButton(BleskIcons.Repeat, stringResource(R.string.media_circle_retake), {
                    player.pause()
                    discard(ui)
                    ui.mode = RecordMode.Circle
                    if (begin(ui, recorders, owner, scope)) ui.locked = true
                })
            }
            Box(Modifier.clip(CircleShape).background(Blesk.colors.surface)) {
                IconButton(BleskIcons.Trash, stringResource(R.string.media_delete_recording), {
                    player.pause()
                    discard(ui)
                })
            }
        }
    }
}

/** The circle's frames in a row, with a handle at each end to cut the start or the end. */
@Composable
private fun FrameStrip(frames: List<ImageBitmap>, from: Float, to: Float, onChange: (Float, Float) -> Unit) {
    val handle = Blesk.colors.accent
    val shade = Blesk.colors.background.copy(alpha = SHADE_ALPHA)
    val label = stringResource(R.string.media_trim)
    var dragging by remember { mutableStateOf<Int?>(null) }
    Box(
        Modifier
            .width(StripWidth)
            .height(StripHeight)
            .clip(RoundedCornerShape(12.dp))
            .background(Blesk.colors.surface)
            .semantics { contentDescription = label }
            .pointerInput(from, to) {
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        val f = at.x / size.width
                        dragging = if (abs(f - from) <= abs(f - to)) 0 else 1
                    },
                    onDragEnd = { dragging = null },
                    onDragCancel = { dragging = null },
                ) { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    when (dragging) {
                        0 -> onChange(f.coerceAtMost(to - MIN_KEPT), to)
                        1 -> onChange(from, f.coerceAtLeast(from + MIN_KEPT))
                    }
                }
            },
    ) {
        Row(Modifier.fillMaxSize()) {
            frames.forEach { frame ->
                Image(frame, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.weight(1f).fillMaxSize())
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            drawRect(shade, size = Size(size.width * from, size.height))
            drawRect(shade, topLeft = Offset(size.width * to, 0f), size = Size(size.width * (1f - to), size.height))
            listOf(from, to).forEach { at ->
                val x = (at * size.width).coerceIn(HandleWidth.toPx() / 2f, size.width - HandleWidth.toPx() / 2f)
                drawRoundRect(
                    color = handle,
                    topLeft = Offset(x - HandleWidth.toPx() / 2f, 0f),
                    size = Size(HandleWidth.toPx(), size.height),
                    cornerRadius = CornerRadius(HandleWidth.toPx() / 2f),
                )
            }
        }
    }
}

private fun clipItem(clip: Clip) = MediaItem.Builder()
    .setUri(clip.path)
    .setClippingConfiguration(
        MediaItem.ClippingConfiguration.Builder().setStartPositionMs(clip.startMillis).setEndPositionMs(clip.endMillis).build(),
    )
    .build()

/** A few frames spread over the circle, small, for the strip under it. */
private fun framesOf(circle: Attachment.Circle): List<ImageBitmap> {
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(circle.path)
        (0 until FRAMES).mapNotNull { i ->
            val at = circle.durationMillis * 1000L * i / FRAMES
            retriever.getScaledFrameAtTime(at, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, FRAME_PX, FRAME_PX)
                ?.let(Bitmap::asImageBitmap)
        }
    } catch (_: RuntimeException) {
        emptyList()
    } finally {
        retriever.release()
    }
}

private val SendHeight = 44.dp
private val PreviewHeight = 52.dp
private val HandleWidth = 6.dp
private val CircleSize = 280.dp
private val StripWidth = 280.dp
private val StripHeight = 48.dp
private const val CUT_ALPHA = 0.18f
private const val SHADE_ALPHA = 0.65f
private const val BAR_SHARE = 0.55f
private const val MIN_KEPT = 0.05f
private const val PROGRESS_MILLIS = 50L
private const val FRAMES = 8
private const val FRAME_PX = 96
