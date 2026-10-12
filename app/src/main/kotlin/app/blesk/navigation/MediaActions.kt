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
