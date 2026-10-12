package app.blesk.navigation

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.visuals
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.chats.R
import app.blesk.feature.chats.media.MediaViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Every photo and video of the chat in one row, opened at the one that was tapped. */
@Composable
internal fun MediaRoute(container: AppContainer, key: MediaKey, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val messages by remember(key.conversationId) { chats.messages(key.conversationId) }.collectAsStateWithLifecycle(initialValue = null)
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val saved = stringResource(R.string.media_saved)
    val failed = stringResource(R.string.media_save_failed)
    val loaded = messages ?: return
    val items = mutableListOf<Attachment>()
    // Which message and album place each page comes from, to stream a video that has not arrived.
    val origins = mutableListOf<Pair<String, Int>>()
    var start = 0
    loaded.forEach { message ->
        val visuals = message.attachment?.visuals.orEmpty()
        if (message.id == key.messageId) start = items.size + key.index.coerceIn(0, (visuals.size - 1).coerceAtLeast(0))
        items += visuals
        visuals.indices.forEach { origins += message.id to it }
    }
    if (items.isEmpty()) return
    MediaViewer(
        items = items,
        start = start,
        onBack = onBack,
        stream = { page -> origins.getOrNull(page)?.let { (messageId, index) -> chats.stream(key.conversationId, messageId, index) } },
        onSave = if (privacy.saveMedia) {
            { item -> scope.launch { notify(if (saveToGallery(context, item)) saved else failed) } }
        } else {
            null
        },
    )
}

/** Copies a photo or video into the shared gallery, under «Pictures/blesk» or «Movies/blesk». */
private suspend fun saveToGallery(context: Context, item: Attachment): Boolean = withContext(Dispatchers.IO) {
    val (path, video) = when (item) {
        is Attachment.Photo -> item.path to false
        is Attachment.Video -> item.path to true
        else -> return@withContext false
    }
    val source = File(path).takeIf { it.exists() } ?: return@withContext false
    val collection = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "blesk_${System.currentTimeMillis()}.${if (video) "mp4" else "jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (video) "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (video) "Movies/blesk" else "Pictures/blesk")
        }
    }
    runCatching {
        val uri = context.contentResolver.insert(collection, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } } != null
    }.getOrDefault(false)
}
