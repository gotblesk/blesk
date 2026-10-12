package app.blesk.feature.chats.media

import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.media.Playback
import app.blesk.core.media.Waveform
import app.blesk.feature.chats.R
import coil3.compose.AsyncImage
import java.io.File
import java.util.Locale

/** Playback and opening of media, provided once per conversation. */
@Immutable
class MediaController(
    val playback: State<Playback>,
    val onToggleVoice: (String) -> Unit,
    val onSeekVoice: (path: String, fraction: Float, durationMillis: Long) -> Unit,
    val onCycleSpeed: () -> Unit,
    /** Opens the viewer on a message's photo or video; [index] is the place within an album. */
    val onOpenMedia: (message: Message, index: Int) -> Unit,
    val onOpenFile: (Attachment.File) -> Unit,
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
internal fun Attachment.isVisual() = this is Attachment.Photo || this is Attachment.Video || this is Attachment.Album

@Composable
internal fun MediaContent(message: Message, attachment: Attachment) {
    val controller = LocalMediaController.current
    when (attachment) {
        is Attachment.Photo -> Picture(
            path = attachment.path,
            ratio = attachment.width.toFloat() / attachment.height.coerceAtLeast(1),
            description = stringResource(R.string.media_photo),
            onClick = { controller.onOpenMedia(message, 0) },
        )
        is Attachment.Video -> Box {
            Picture(
                path = attachment.thumbnail,
                ratio = attachment.width.toFloat() / attachment.height.coerceAtLeast(1),
                description = stringResource(R.string.media_video, formatDuration(attachment.durationMillis)),
                onClick = { controller.onOpenMedia(message, 0) },
            )
            PlayBadge(Modifier.align(Alignment.Center))
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
        is Attachment.File -> FileRow(attachment) { controller.onOpenFile(attachment) }
        is Attachment.Voice -> VoiceNote(attachment, controller)
        is Attachment.Circle -> CircleNote(attachment)
        is Attachment.Theme -> Unit
        is Attachment.Album -> AlbumMosaic(attachment) { index -> controller.onOpenMedia(message, index) }
    }
}

@Composable
private fun Picture(path: String, ratio: Float, description: String, onClick: () -> Unit) {
    AsyncImage(
        model = File(path),
        contentDescription = description,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .widthIn(max = 260.dp)
            .width(260.dp)
            .aspectRatio(ratio.coerceIn(MIN_RATIO, MAX_RATIO))
            .clip(RoundedCornerShape(16.dp))
            .veiled()
            .clickable(role = Role.Image, onClick = onClick),
    )
}

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
private fun FileRow(file: Attachment.File, onOpen: () -> Unit) {
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
            Text(formatSize(file.sizeBytes), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        }
    }
}

@Composable
private fun VoiceNote(voice: Attachment.Voice, controller: MediaController) {
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
                ) { controller.onToggleVoice(voice.path) },
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
private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> stringResource(R.string.media_size_b, bytes.toString())
    bytes < 1024 * 1024 -> stringResource(R.string.media_size_kb, String.format(Locale.getDefault(), "%.0f", bytes / 1024.0))
    else -> stringResource(R.string.media_size_mb, String.format(Locale.getDefault(), "%.1f", bytes / (1024.0 * 1024.0)))
}
