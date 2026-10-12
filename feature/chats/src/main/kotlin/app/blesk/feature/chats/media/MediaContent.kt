package app.blesk.feature.chats.media

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Transfer
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.motion.sharedElement
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Playback
import app.blesk.core.media.Waveform
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import java.io.File
import java.util.Base64
import java.util.Locale

/** Playback and opening of media, provided once per conversation. */
@Immutable
class MediaController(
    val playback: State<Playback>,
    /** Plays or pauses a voice note or song; voice notes after it in the chat follow. */
    val onPlay: (Message) -> Unit,
    val onSeekVoice: (path: String, fraction: Float, durationMillis: Long) -> Unit,
    val onCycleSpeed: () -> Unit,
    /** Opens the viewer on a message's photo or video; [index] is the place within an album. */
    val onOpenMedia: (message: Message, index: Int) -> Unit,
    val onOpenFile: (Attachment.File) -> Unit,
    /** Stops a file on its way: an upload takes the unsent message with it. */
    val onCancelTransfer: (Message) -> Unit = {},
    /** Starts a stopped download again from where it was. */
    val onRetryTransfer: (Message) -> Unit = {},
) {
    companion object {
        val Inert = MediaController(mutableStateOf(Playback()), {}, { _, _, _ -> }, {}, { _, _ -> }, {})
    }
}

val LocalMediaController = staticCompositionLocalOf { MediaController.Inert }

/** In a message request, pictures stay blurred until it is accepted: nobody can push an image at you. */
val LocalVeiledMedia = staticCompositionLocalOf { false }

/** Blurs a picture in a request; before Android 12, where blur does not draw, a scrim covers it instead. */
@Composable
internal fun Modifier.veiled(): Modifier {
    if (!LocalVeiledMedia.current) return this
    val scrim = Blesk.colors.surfaceStrong
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        blur(VeilBlur)
    } else {
        drawWithContent {
            drawContent()
            drawRect(scrim.copy(alpha = VEIL_SCRIM))
        }
    }
}

private val VeilBlur = 28.dp
private const val VEIL_SCRIM = 0.9f

/** True when the attachment fills the bubble edge to edge rather than sitting above text. */
/** Ties a picture in the chat to its page in the viewer, so it grows out of its place and back. */
fun mediaKey(messageId: String, index: Int) = "media:$messageId/$index"

/** A sticker in the chat: big enough to read its face, small enough to leave room for words. */
val StickerSize = 160.dp

internal fun Attachment.isVisual() = this is Attachment.Photo || this is Attachment.Video || this is Attachment.Album

@Composable
internal fun MediaContent(message: Message, attachment: Attachment) {
    val controller = LocalMediaController.current
    when (attachment) {
        is Attachment.Photo -> Picture(
            path = attachment.path,
            preview = attachment.preview,
            ratio = attachment.width.toFloat() / attachment.height.coerceAtLeast(1),
            description = stringResource(R.string.media_photo),
            sharedKey = mediaKey(message.id, 0),
            onClick = { controller.onOpenMedia(message, 0) },
            transfer = transferOverlay(message, message.id, attachment),
        )
        is Attachment.Video -> Box {
            Picture(
                path = attachment.thumbnail,
                preview = attachment.preview,
                ratio = attachment.width.toFloat() / attachment.height.coerceAtLeast(1),
                description = stringResource(R.string.media_video, formatDuration(attachment.durationMillis)),
                sharedKey = mediaKey(message.id, 0),
                onClick = { controller.onOpenMedia(message, 0) },
                transfer = transferOverlay(message, message.id, attachment),
            )
            if (LocalTransfers.current[message.id] == null) PlayBadge(Modifier.align(Alignment.Center))
            Text(
                text = formatDuration(attachment.durationMillis),
                style = Blesk.type.caption,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        is Attachment.File -> {
            // A file left for a tap offers the same button as one whose download stopped.
            val transfer = LocalTransfers.current[message.id]
                ?: Transfer(0, -1, failed = true).takeIf { message.pending && attachment.path.isEmpty() }
            FileRow(attachment, transfer, { controller.onOpenFile(attachment) }) {
                if (transfer?.failed == true) controller.onRetryTransfer(message) else controller.onCancelTransfer(message)
            }
        }
        is Attachment.Voice -> VoiceNote(attachment, controller) { controller.onPlay(message) }
        is Attachment.Music -> MusicRow(attachment, controller) { controller.onPlay(message) }
        is Attachment.Circle -> CircleNote(attachment)
        is Attachment.Theme -> Unit
        is Attachment.Album -> AlbumMosaic(attachment, message) { index -> controller.onOpenMedia(message, index) }
        is Attachment.Sticker -> StickerView(attachment.path, attachment.format, Modifier.size(StickerSize))
    }
}

@Composable
private fun Picture(
    path: String,
    preview: String,
    ratio: Float,
    description: String,
    sharedKey: String,
    onClick: () -> Unit,
    transfer: (@Composable BoxScope.() -> Unit)? = null,
) {
    // The size is known from the message, so the bubble never jumps when the picture arrives.
    Box(
        Modifier
            .widthIn(max = 260.dp)
            .width(260.dp)
            .aspectRatio(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
            .sharedElement(sharedKey)
            .clip(RoundedCornerShape(16.dp))
            .background(Blesk.colors.surfaceStrong)
            .veiled()
            .clickable(role = Role.Image, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
    ) {
        if (path.isEmpty()) {
            BlurredPreview(preview, Modifier.matchParentSize())
        } else {
            AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        transfer?.invoke(this)
    }
}

/** The ring over a picture of [message] while [tag] is on its way. */
@Composable
internal fun transferOverlay(message: Message, tag: String, item: Attachment?): (@Composable BoxScope.() -> Unit)? {
    val controller = LocalMediaController.current
    val transfer = LocalTransfers.current[tag]
    if (transfer == null) {
        // Not on its way yet: «Автозагрузка» left it for a tap, which shows how much it is.
        if (!message.pending || item == null || item.arrived()) return null
        return {
            Waiting(item.downloadSize(), { controller.onRetryTransfer(message) }, Modifier.align(Alignment.Center))
        }
    }
    return {
        TransferRing(
            transfer = transfer,
            onCancel = { controller.onCancelTransfer(message) },
            onRetry = { controller.onRetryTransfer(message) },
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** A tiny picture sent along with the message, blurred, for while the real one is on its way. */
@Composable
internal fun BlurredPreview(base64: String, modifier: Modifier = Modifier) {
    val bitmap = remember(base64) {
        base64.takeIf { it.isNotEmpty() }?.let { data ->
            runCatching {
                val bytes = Base64.getDecoder().decode(data)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    } ?: return
    Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.blur(PreviewBlur))
}

private val PreviewBlur = 12.dp

private const val MIN_RATIO = 0.6f
private const val MAX_RATIO = 1.8f

@Composable
internal fun PlayBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(48.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BleskIcons.Play, contentDescription = null, tint = Color.White)
    }
}

@Composable
private fun FileRow(file: Attachment.File, transfer: Transfer?, onOpen: () -> Unit, onTransfer: () -> Unit) {
    val label = stringResource(R.string.media_file_open, file.name)
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClickLabel = label, onClick = onOpen)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).background(Blesk.colors.background, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(BleskIcons.Document, contentDescription = null)
        }
        Column(Modifier.widthIn(max = 200.dp)) {
            Text(file.name, style = Blesk.type.bodyStrong, maxLines = 2)
            val size = transfer?.takeIf { it.total > 0 }
                ?.let { stringResource(R.string.media_transfer, formatSize(it.done), formatSize(it.total)) }
                ?: formatSize(file.sizeBytes)
            Text(size, style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        }
        if (transfer != null) {
            IconButton(
                icon = if (transfer.failed) BleskIcons.Download else BleskIcons.Close,
                contentDescription = stringResource(if (transfer.failed) R.string.media_retry else R.string.media_transfer_cancel),
                onClick = onTransfer,
            )
        }
    }
}

@Composable
private fun VoiceNote(voice: Attachment.Voice, controller: MediaController, onToggle: () -> Unit) {
    val playback = controller.playback.value
    val active = playback.path == voice.path
    val playing = active && playback.playing
    val progress = if (active) (playback.positionMillis.toFloat() / voice.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
    val bars = remember(voice.waveform) { Waveform.decode(voice.waveform) }
    val description = stringResource(R.string.media_voice, formatDuration(voice.durationMillis))
    val lit = Blesk.colors.textPrimary
    val dim = Blesk.colors.textTertiary
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Blesk.colors.accent)
                .clickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (playing) R.string.media_pause else R.string.media_play),
                ) { onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) BleskIcons.Pause else BleskIcons.Play, contentDescription = null, tint = Blesk.colors.onAccent, size = 20.dp)
        }
        Column {
            Canvas(
                Modifier
                    .width(150.dp)
                    .height(28.dp)
                    .pointerInput(voice.path) {
                        detectTapGestures { controller.onSeekVoice(voice.path, it.x / size.width, voice.durationMillis) }
                    },
            ) {
                val step = size.width / bars.size
                val bar = step * 0.55f
                bars.forEachIndexed { i, value ->
                    val h = (value * size.height).coerceAtLeast(bar)
                    drawRoundRect(
                        color = if (i.toFloat() / bars.size < progress) lit else dim,
                        topLeft = Offset(i * step, (size.height - h) / 2f),
                        size = Size(bar, h),
                        cornerRadius = CornerRadius(bar / 2f),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDuration(if (active) playback.positionMillis else voice.durationMillis),
                    style = Blesk.type.caption,
                    color = Blesk.colors.textSecondary,
                )
                if (active) {
                    val speed = formatSpeed(playback.speed)
                    Text(
                        text = speed,
                        style = Blesk.type.caption,
                        color = Blesk.colors.textPrimary,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Blesk.colors.background)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = stringResource(R.string.media_speed, speed),
                                onClick = controller.onCycleSpeed,
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CircleNote(circle: Attachment.Circle) {
    val description = stringResource(R.string.media_circle, formatDuration(circle.durationMillis))
    CirclePlayer(
        circle = circle,
        modifier = Modifier
            .size(220.dp)
            .semantics { contentDescription = description },
    )
}

internal fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

private fun formatSpeed(speed: Float) = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@Composable
internal fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.media_size_b, bytes.toString())
    bytes < 1024 * 1024 -> stringResource(R.string.media_size_kb, String.format(Locale.getDefault(), "%.0f", bytes / 1024.0))
    else -> stringResource(R.string.media_size_mb, String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0)))
}

/** A file left for a tap: a download button with its size under it. */
@Composable
private fun Waiting(size: Long, onDownload: () -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(R.string.media_download)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onDownload)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(BleskIcons.Download, contentDescription = null, tint = Color.White, size = 20.dp)
        }
        if (size > 0) {
            Text(
                text = formatSize(size),
                style = Blesk.type.caption,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

private fun Attachment.arrived(): Boolean = when (this) {
    is Attachment.Photo -> path.isNotEmpty()
    is Attachment.Video -> path.isNotEmpty()
    is Attachment.File -> path.isNotEmpty()
    else -> true
}

private fun Attachment.downloadSize(): Long = when (this) {
    is Attachment.Photo -> sizeBytes
    is Attachment.Video -> sizeBytes
    is Attachment.File -> sizeBytes
    else -> 0
}

/** A song in the chat: play and pause, what it is and who sings it, its length and size; a tap on the line seeks. */
@Composable
private fun MusicRow(music: Attachment.Music, controller: MediaController, onToggle: () -> Unit) {
    val playback = controller.playback.value
    val active = music.path.isNotEmpty() && playback.path == music.path
    val playing = active && playback.playing
    val progress = if (active) (playback.positionMillis.toFloat() / music.durationMillis.coerceAtLeast(1)).coerceIn(0f, 1f) else 0f
    val label = stringResource(if (playing) R.string.media_pause else R.string.media_play)
    Row(
        modifier = Modifier.widthIn(max = 260.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Blesk.colors.background)
                .clickable(enabled = music.path.isNotEmpty(), role = Role.Button, onClickLabel = label, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(if (playing) BleskIcons.Pause else BleskIcons.Music, contentDescription = null)
        }
        Column(Modifier.weight(1f, fill = false)) {
            Text(music.title, style = Blesk.type.bodyStrong, maxLines = 1)
            if (music.artist.isNotEmpty()) Text(music.artist, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
            if (active) {
                val track = Blesk.colors.hairline
                val fill = Blesk.colors.light
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(track)
                        .pointerInput(music.path) {
                            detectTapGestures { controller.onSeekVoice(music.path, it.x / size.width, music.durationMillis) }
                        },
                ) {
                    Box(Modifier.fillMaxWidth(progress).height(4.dp).background(fill))
                }
            }
            Text(
                text = "${formatDuration(if (active) playback.positionMillis else music.durationMillis)} · ${formatSize(music.sizeBytes)}",
                style = Blesk.type.caption,
                color = Blesk.colors.textSecondary,
            )
        }
    }
}
