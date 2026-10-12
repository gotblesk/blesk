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
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.model.files
import app.blesk.core.data.settings.OfflineKey
import app.blesk.feature.settings.ChatUse
import app.blesk.feature.settings.OfflineItem
import app.blesk.feature.settings.OfflineScreen
import app.blesk.feature.settings.StorageActions
import app.blesk.feature.settings.StorageScreen
import app.blesk.feature.settings.StorageUse
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

@Composable
internal fun StorageRoute(container: AppContainer, onOpenOffline: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats by container.chats.collectAsState()
    val keepMediaDays by container.settings.keepMediaDays.collectAsState(0)
    val autoDownload by container.autoDownload.collectAsState()
    val kept by container.keptOffline.keys.collectAsState(emptySet())
    var version by remember { mutableIntStateOf(0) }
    val use by produceState<StorageUse?>(null, version, chats) { value = withContext(Dispatchers.IO) { measure(context, chats) } }
    StorageScreen(
        use = use,
        keepMediaDays = keepMediaDays,
        autoDownload = autoDownload,
        offlineCount = kept.size,
        actions = StorageActions(
            onOpenOffline = onOpenOffline,
            onAutoDownload = { auto -> scope.launch { container.settings.setAutoDownload(auto) } },
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
        files = total { it is Attachment.File || it is Attachment.Music },
        database = context.getDatabasePath("blesk.db").parentFile?.sizeOnDisk() ?: 0L,
        cache = context.cacheDir.sizeOnDisk(),
        chats = byChat.toImmutableList(),
    )
}

@Serializable
data object KeptOfflineKey : NavKey

/** «Сохранённое офлайн»: each kept file with its chat; a message that is gone drops off the list. */
@Composable
internal fun OfflineRoute(container: AppContainer, onOpen: (conversationId: String, messageId: String) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chats = container.chats.collectAsState().value ?: return
    val keys by container.keptOffline.keys.collectAsState(emptySet())
    val transfers by chats.transfers.collectAsState()
    val items by produceState<ImmutableList<OfflineItem>>(persistentListOf(), keys, chats, transfers) {
        value = withContext(Dispatchers.IO) {
            keys.groupBy { it.conversationId }.flatMap { (conversationId, inChat) ->
                val messages = chats.messages(conversationId).first().associateBy { it.id }
                val chat = chats.conversation(conversationId).first()?.title.orEmpty()
                inChat.mapNotNull { key ->
                    val message = messages[key.messageId]
                    val attachment = message?.attachment
                    if (attachment == null) {
                        container.keptOffline.set(key, false)
                        return@mapNotNull null
                    }
                    val progress = if (message.pending) transfers[message.id]?.fraction ?: 0f else null
                    OfflineItem(conversationId, message.id, offlineTitle(context, attachment), chat, offlineBytes(attachment), progress)
                }
            }.toImmutableList()
        }
    }
    OfflineScreen(
        items = items,
        onOpen = { item -> onOpen(item.conversationId, item.messageId) },
        onRemove = { item -> scope.launch { container.keptOffline.set(OfflineKey(item.conversationId, item.messageId), false) } },
        onBack = onBack,
    )
}

private fun offlineTitle(context: Context, attachment: Attachment): String = when (attachment) {
    is Attachment.File -> attachment.name
    is Attachment.Music -> attachment.title
    else -> context.resources.attachmentLabel(attachment)
}

/** What is on the phone, or what is still to come for a file not yet here. */
private fun offlineBytes(attachment: Attachment): Long {
    val here = attachment.files.sumOf { File(it).length() }
    if (here > 0) return here
    return when (attachment) {
        is Attachment.Video -> attachment.sizeBytes
        is Attachment.File -> attachment.sizeBytes
        is Attachment.Music -> attachment.sizeBytes
        else -> 0
    }
}

private fun File.sizeOnDisk(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }

private const val MAX_CHATS = 10
