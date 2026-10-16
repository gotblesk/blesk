package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.settings.ChatLocks
import app.blesk.feature.chats.ChatMenu
import app.blesk.feature.chats.ChatMenuActions
import app.blesk.locks.lockOf
import app.blesk.locks.setLocked
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import app.blesk.feature.chats.R as ChatsR

/**
 * The long press on a chat in a list: the mini-chat with its latest messages, read without
 * marking anything read, and the chat's actions; a chat deleted from it waits out «Отменить».
 */
@Composable
internal fun ConversationMenu(
    container: AppContainer,
    chats: ChatRepository,
    conversation: Conversation,
    all: List<Conversation>,
    anchor: Rect?,
    onPinLimit: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onNeedCode: () -> Unit,
) {
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    val open by container.lockSession.open.collectAsStateWithLifecycle()
    val lockId = locks.lockOf(conversation.id)
    // A locked chat that is not open shows its lock in the mini-chat, never its messages.
    val hidden = lockId != null && lockId !in open
    val messages by remember(conversation.id) {
        chats.messages(conversation.id).map { it.takeLast(PREVIEW_MESSAGES) }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val deleted = stringResource(ChatsR.string.person_chat_deleted)
    val id = conversation.id
    val delete: ((Boolean) -> Unit)? = if (conversation.kind == ConversationKind.Direct) {
        { forBoth -> container.notices.undoable(deleted, hidden = listOf(id)) { chats.deleteChat(id, forBoth) } }
    } else {
        null
    }
    ChatMenu(
        conversation = conversation,
        now = System.currentTimeMillis(),
        actions = chatMenuActions(chats, conversation, all, onPinLimit, delete).let { base ->
            ChatMenuActions(
                onPin = base.onPin,
                onArchive = base.onArchive,
                onMute = base.onMute,
                onMarkUnread = base.onMarkUnread,
                onDelete = base.onDelete,
                onLock = { lock -> container.setLocked(lockId ?: id, lock, onNeedCode) },
                locked = lockId != null,
            )
        },
        onDismiss = onDismiss,
        anchor = anchor,
        messages = if (hidden) persistentListOf() else messages.filter { it.id !in container.notices.hidden }.toImmutableList(),
        authorName = { author -> chats.contact(author)?.name },
        onOpen = { onOpen(id) },
    )
}

private const val PREVIEW_MESSAGES = 40
