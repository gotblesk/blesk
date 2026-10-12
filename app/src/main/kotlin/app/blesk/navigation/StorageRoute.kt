package app.blesk.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.blesk.AppContainer
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.files
import app.blesk.feature.settings.ChatUse
import app.blesk.feature.settings.StorageActions
import app.blesk.feature.settings.StorageScreen
import app.blesk.feature.settings.StorageUse
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun StorageRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by container.chats.collectAsState()
    val keepMediaDays by container.settings.keepMediaDays.collectAsState(0)
    var version by remember { mutableIntStateOf(0) }
    val use by produceState<StorageUse?>(null, version, chats) { value = withContext(Dispatchers.IO) { measure(context, chats) } }
    StorageScreen(
        use = use,
        keepMediaDays = keepMediaDays,
        actions = StorageActions(
            onKeepMedia = { days ->
                scope.launch {
                    container.settings.setKeepMediaDays(days)
                    chats?.let { container.clearOldMedia(it, days) }
                    version++
                }
            },
            onClearChat = { id ->
                scope.launch {
                    chats?.clearMedia(id, Long.MAX_VALUE)
                    version++
                }
            },
            onClearCache = {
                scope.launch {
                    withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                    version++
                }
            },
            onBack = onBack,
        ),
    )
}

private suspend fun measure(context: Context, chats: ChatRepository?): StorageUse {
    val stored = chats?.storedMedia().orEmpty()
    fun bytes(attachment: Attachment) = attachment.files.sumOf { File(it).length() }
    fun total(kind: (Attachment) -> Boolean) = stored.filter { kind(it.attachment) }.sumOf { bytes(it.attachment) }
    val byChat = stored.groupBy { it.conversationId }
        .mapValues { (_, items) -> items.sumOf { bytes(it.attachment) } }
        .filterValues { it > 0 }
        .entries
        .sortedByDescending { it.value }
        .take(MAX_CHATS)
        .map { (id, size) -> ChatUse(id, chats?.conversation(id)?.first()?.title ?: id, size) }
    return StorageUse(
        photos = total { it is Attachment.Photo },
        videos = total { it is Attachment.Video || it is Attachment.Circle },
        voice = total { it is Attachment.Voice },
        files = total { it is Attachment.File },
        database = context.getDatabasePath("blesk.db").parentFile?.sizeOnDisk() ?: 0L,
        cache = context.cacheDir.sizeOnDisk(),
        chats = byChat.toImmutableList(),
    )
}

private fun File.sizeOnDisk(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }

private const val MAX_CHATS = 10
