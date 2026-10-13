package app.blesk.feature.chats.media

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.HeaderArt
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.avatarHue
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Playback
import app.blesk.core.media.Repeat
import app.blesk.core.media.TrackAnalyzer
import app.blesk.core.media.TrackFormat
import app.blesk.core.media.TrackInfo
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What the open player can be asked to do. */
class PlayerActions(
    val toggle: () -> Unit,
    val next: () -> Unit,
    val previous: () -> Unit,
    val seek: (fraction: Float) -> Unit,
    val cycleRepeat: () -> Unit,
    val toggleShuffle: () -> Unit,
    val playAt: (index: Int) -> Unit,
    /** Keeps the song on the phone for listening without internet; null when it is not from a chat. */
    val keepOffline: ((Boolean) -> Unit)?,
    val keptOffline: Boolean,
    /** «Поделиться в чат»; null when the song is not from a chat. */
    val share: (() -> Unit)?,
)

private enum class PlayerTab { Now, Queue, Library }

/**
 * The whole player, poured out of the small one over the current screen. «Сейчас»: where the
 * song is from, its cover (or «Свет» in a hue of its own), which pulses with the music, the real
 * waveform to seek along, the controls, where the sound goes and the format. «Очередь» and
 * «Библиотека» next to it. Swiped down by its top it flows back into the small player.
 */
@Composable
fun PlayerSheet(
    playback: Playback,
    level: () -> Float,
    output: String,
    actions: PlayerActions,
    library: @Composable () -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 12.dp)
                .fillMaxWidth()
                .fillMaxHeight()
                .clip(RoundedCornerShape(topStart = SheetCorner, topEnd = SheetCorner))
                .background(Blesk.colors.background)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .windowInsetsPadding(WindowInsets.navigationBars),
        ) {
            Column(Modifier.fillMaxWidth().dragToDismiss(onClose), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 10.dp).size(width = 36.dp, height = 4.dp).clip(CircleShape).background(Blesk.colors.hairline))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(BleskIcons.ArrowDown, stringResource(R.string.player_close), onClose)
                    val from = playback.track?.from.orEmpty()
                    Text(
                        text = if (from.isNotEmpty()) stringResource(R.string.player_from, from) else "",
                        style = Blesk.type.label,
                        color = Blesk.colors.textSecondary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    Box(Modifier.size(48.dp))
                }
            }
            Segmented(
                options = persistentListOf(
                    stringResource(R.string.player_tab_now),
                    stringResource(R.string.player_tab_queue),
                    stringResource(R.string.player_tab_library),
                ),
                selected = tab,
                onSelect = { tab = it },
            )
            when (PlayerTab.entries[tab]) {
                PlayerTab.Now -> NowPlaying(playback, level, output, actions)
                PlayerTab.Queue -> Queue(playback, actions)
                PlayerTab.Library -> library()
            }
        }
    }
}

@Composable
private fun NowPlaying(playback: Playback, level: () -> Float, output: String, actions: PlayerActions) {
    val path = playback.path ?: return
    val info by produceState(TrackAnalyzer.cached(path), path) {
        value = withContext(Dispatchers.Default) { TrackAnalyzer.analyze(path) }
    }
    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Cover(playback.title, info, level, Modifier.fillMaxWidth(COVER_SHARE).aspectRatio(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = playback.title.ifEmpty { stringResource(R.string.player_untitled) },
                style = Blesk.type.title,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
            if (playback.artist.isNotEmpty()) Text(playback.artist, color = Blesk.colors.textSecondary, maxLines = 1)
        }
        Wave(playback, info?.bars, actions.seek)
        Row(Modifier.fillMaxWidth()) {
            val caption = Blesk.type.caption
            Text(clock(playback.positionMillis), style = caption, color = Blesk.colors.textSecondary, modifier = Modifier.weight(1f))
            Text(clock(playback.durationMillis), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        }
        Controls(playback, actions)
        Text(stringResource(R.string.player_output, output), style = Blesk.type.label, color = Blesk.colors.textSecondary)
        actions.keepOffline?.let { keep ->
            ToggleRow(
                title = stringResource(R.string.player_offline),
                checked = actions.keptOffline,
                onCheckedChange = keep,
                icon = BleskIcons.Download,
            )
        }
        actions.share?.let { share -> ListRow(title = stringResource(R.string.player_share), icon = BleskIcons.Forward, onClick = share) }
        info?.format?.let { Text(formatLine(it), style = Blesk.type.caption, color = Blesk.colors.textTertiary) }
    }
}

/** The cover from the tags, or «Свет» in a hue taken from the title; either breathes with the loudness. */
@Composable
private fun Cover(title: String, info: TrackInfo?, level: () -> Float, modifier: Modifier) {
    val bytes = info?.cover
    val picture by produceState<ImageBitmap?>(null, bytes) {
        value = bytes?.let { data ->
            withContext(Dispatchers.Default) {
                runCatching { BitmapFactory.decodeByteArray(data, 0, data.size)?.asImageBitmap() }.getOrNull()
            }
        }
    }
    val light = Blesk.colors.light
    val reduced = Blesk.motion.reduced
    Box(
        modifier
            .graphicsLayer {
                val swell = if (reduced) 0f else level()
                scaleX = 1f + swell * PULSE
                scaleY = scaleX
            }
            .clip(RoundedCornerShape(CoverCorner)),
    ) {
        val shown = picture
        if (shown != null) {
            Image(shown, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val preset = title.hashCode().mod(HeaderStyle.Light.PRESETS)
            HeaderArt(HeaderStyle.Light(preset), photo = null, modifier = Modifier.fillMaxSize(), hue = avatarHue(title))
        }
        // The light over the cover follows the music itself.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (reduced) 0f else level() * GLOW }
                .background(Brush.radialGradient(listOf(light, Color.Transparent))),
        )
    }
}

/** The song's real waveform: what has played glows; a tap or a slide seeks, with a tick at every tenth. */
@Composable
private fun Wave(playback: Playback, bars: FloatArray?, onSeek: (Float) -> Unit) {
    val haptics = rememberHaptics()
    val played = Blesk.colors.light
    val ahead = Blesk.colors.hairline
    val fraction = if (playback.durationMillis > 0) (playback.positionMillis.toFloat() / playback.durationMillis).coerceIn(0f, 1f) else 0f
    var dragging by remember { mutableFloatStateOf(-1f) }
    var lastTenth by remember { mutableIntStateOf(-1) }
    val label = stringResource(R.string.player_wave)
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(WaveHeight)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                setProgress { value -> onSeek(value); true }
            }
            .pointerInput(onSeek) { detectTapGestures { onSeek(it.x / size.width) } }
            .pointerInput(onSeek) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragging >= 0f) onSeek(dragging)
                        dragging = -1f
                    },
                ) { change, _ ->
                    val f = (change.position.x / size.width).coerceIn(0f, 1f)
                    val tenth = (f * TENTHS).toInt()
                    if (tenth != lastTenth) {
                        lastTenth = tenth
                        haptics.tick()
                    }
                    dragging = f
                }
            },
    ) {
        val shown = bars ?: FloatArray(PLACEHOLDER_BARS) { PLACEHOLDER_LEVEL }
        val step = size.width / shown.size
        val barWidth = step * BAR_SHARE
        val at = if (dragging >= 0f) dragging else fraction
        shown.forEachIndexed { index, value ->
            val h = (value.coerceIn(MIN_BAR, 1f)) * size.height
            val x = index * step + (step - barWidth) / 2f
            drawRoundRect(
                color = if ((index + 0.5f) / shown.size <= at) played else ahead,
                topLeft = Offset(x, (size.height - h) / 2f),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }
    }
}

@Composable
private fun Controls(playback: Playback, actions: PlayerActions) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        IconButton(
            icon = BleskIcons.Shuffle,
            contentDescription = stringResource(if (playback.shuffle) R.string.player_shuffle_off else R.string.player_shuffle_on),
            onClick = actions.toggleShuffle,
            tint = if (playback.shuffle) Blesk.colors.textPrimary else Blesk.colors.textTertiary,
        )
        IconButton(BleskIcons.SkipBack, stringResource(R.string.player_previous), actions.previous)
        val play = stringResource(if (playback.playing) R.string.media_pause else R.string.media_play)
        Icon(
            icon = if (playback.playing) BleskIcons.Pause else BleskIcons.Play,
            contentDescription = play,
            tint = Blesk.colors.onAccent,
            size = 32.dp,
            modifier = Modifier
                .size(PlayButton)
                .clip(CircleShape)
                .background(Blesk.colors.accent)
                .clickable(role = Role.Button, onClickLabel = play, onClick = actions.toggle)
                .padding(20.dp),
        )
        IconButton(BleskIcons.SkipForward, stringResource(R.string.player_next), actions.next)
        IconButton(
            icon = if (playback.repeat == Repeat.One) BleskIcons.RepeatOne else BleskIcons.Repeat,
            contentDescription = stringResource(
                when (playback.repeat) {
                    Repeat.Off -> R.string.player_repeat_off
                    Repeat.All -> R.string.player_repeat_all
                    Repeat.One -> R.string.player_repeat_one
                },
            ),
            onClick = actions.cycleRepeat,
            tint = if (playback.repeat == Repeat.Off) Blesk.colors.textTertiary else Blesk.colors.textPrimary,
        )
    }
}

/** What plays next: the songs of the same chat, in order. */
@Composable
private fun Queue(playback: Playback, actions: PlayerActions) {
    if (playback.queue.isEmpty()) return StateMessage(stringResource(R.string.player_queue_empty))
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
        playback.queue.forEachIndexed { index, track ->
            val current = index == playback.index
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { actions.playAt(index) }
                    .padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon = if (current && playback.playing) BleskIcons.Pause else BleskIcons.Music,
                    contentDescription = null,
                    tint = if (current) Blesk.colors.textPrimary else Blesk.colors.textTertiary,
                )
                Column(Modifier.weight(1f)) {
                    Text(track.title.ifEmpty { stringResource(R.string.player_untitled) }, style = Blesk.type.bodyStrong, maxLines = 1)
                    if (track.artist.isNotEmpty()) Text(track.artist, style = Blesk.type.caption, color = Blesk.colors.textSecondary)
                }
                Text(clock(track.durationMillis), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
                if (current) Box(Modifier.width(3.dp).height(20.dp).clip(CircleShape).background(Blesk.colors.light))
            }
        }
    }
}

/** «FLAC · 24 бит · 48 кГц» for lossless files, «MP3 · 320 кбит/с» for the rest. */
@Composable
private fun formatLine(format: TrackFormat): String {
    val khz = format.sampleRateHz?.let { if (it % HZ_PER_KHZ == 0) "${it / HZ_PER_KHZ}" else "%.1f".format(it / HZ_PER_KHZ.toFloat()) }
    val bits = format.bits
    val bitrate = format.bitrateKbps
    return when {
        bits != null && khz != null -> stringResource(R.string.player_format_lossless, format.codec, bits, khz)
        bitrate != null -> stringResource(R.string.player_format_lossy, format.codec, bitrate)
        else -> format.codec
    }
}

internal fun clock(millis: Long): String {
    val seconds = (millis / MILLIS_PER_SECOND).coerceAtLeast(0)
    return "%d:%02d".format(seconds / SECONDS_PER_MINUTE, seconds % SECONDS_PER_MINUTE)
}

private val SheetCorner = 28.dp
private val CoverCorner = 28.dp
private val WaveHeight = 56.dp
private val PlayButton = 72.dp
private const val SCRIM = 0.4f
private const val COVER_SHARE = 0.78f
private const val PULSE = 0.04f
private const val GLOW = 0.45f
private const val TENTHS = 10
private const val BAR_SHARE = 0.6f
private const val MIN_BAR = 0.06f
private const val PLACEHOLDER_BARS = 48
private const val PLACEHOLDER_LEVEL = 0.15f
private const val HZ_PER_KHZ = 1000
private const val MILLIS_PER_SECOND = 1000L
private const val SECONDS_PER_MINUTE = 60
