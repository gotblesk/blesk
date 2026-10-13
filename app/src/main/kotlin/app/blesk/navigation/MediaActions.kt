package app.blesk.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.FileProvider
import androidx.media3.common.util.UnstableApi
import app.blesk.AppContainer
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.media.AudioPlayer
import app.blesk.core.media.Track
import java.io.File

/** Photos are re-encoded on the way in; videos are compressed to 720p first. */
@OptIn(UnstableApi::class)
internal suspend fun importVisual(container: AppContainer, mime: String?, uri: Uri, original: Boolean = false): Attachment? {
    if (mime?.startsWith("video/") != true) return container.media.importPhoto(uri, original)
    val output = container.media.newFile("mp4")
    if (!container.videoCompressor.compress(uri, output, original)) return null
    return container.media.describeVideo(output)
}

/** Hands a received file to another app through a temporary, read-only content link. */
internal fun openFile(context: Context, file: Attachment.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(file.path))
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, file.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(Intent.createChooser(intent, file.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        return
    }
}

/** Opens a bot's https link in the browser; anything else is ignored. */
internal fun openLink(context: Context, url: String) {
    if (!url.startsWith("https://")) return
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
        return
    }
}

/**
 * Plays [message]'s voice note or song. Voice notes after it in the chat follow on their own; the
 * system player shows [voiceTitle] for them, never who sent them.
 */
internal fun playFrom(player: AudioPlayer, message: Message, messages: List<Message>, voiceTitle: String, from: String = "") {
    fun trackOf(item: Message): Track? = when (val attachment = item.attachment) {
        is Attachment.Voice -> attachment.path.takeIf { it.isNotEmpty() }
            ?.let { Track(it, voiceTitle, durationMillis = attachment.durationMillis, from = from) }
        is Attachment.Music -> attachment.path.takeIf { it.isNotEmpty() }?.let { trackOf(item, attachment, from) }
        else -> null
    }
    val track = trackOf(message) ?: return
    // Voice notes play on into the ones after them; a song is followed by the other songs of the chat.
    val music = message.attachment is Attachment.Music
    val next = messages.dropWhile { it.id != message.id }.drop(1)
        .filter { if (music) it.attachment is Attachment.Music else it.attachment is Attachment.Voice }
        .mapNotNull(::trackOf)
    player.toggle(track, next)
}
