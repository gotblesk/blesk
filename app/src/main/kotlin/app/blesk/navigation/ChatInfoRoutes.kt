package app.blesk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.GroupController
import app.blesk.core.data.KeyState
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.chats.ForwardScreen
import app.blesk.feature.chats.PersonActions
import app.blesk.feature.chats.PersonProfileScreen
import app.blesk.feature.chats.R
import app.blesk.locks.hides
import app.blesk.shared.appearance.Appearance
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
internal fun ChatInfoRoute(
    container: AppContainer,
    id: String,
    onVerify: () -> Unit,
    onOpenMedia: (messageId: String) -> Unit,
    onCall: ((video: Boolean) -> Unit)?,
    onChatDeleted: () -> Unit,
    onBack: () -> Unit,
    onOpenConversation: (String) -> Unit = {},
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversation by remember(id) { chats.conversation(id) }.collectAsStateWithLifecycle(initialValue = null)
    val keys = chats as? KeyVerification
    val keyState by remember(id, keys) { keys?.keyState(id) ?: flowOf(KeyState(false, false)) }
        .collectAsStateWithLifecycle(initialValue = KeyState(false, false))
    val scope = rememberCoroutineScope()
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    val chatThemes by container.settings.chatThemes.collectAsStateWithLifecycle(initialValue = emptyMap())
    var themeSheet by remember { mutableStateOf(false) }
    val caption = stringResource(app.blesk.R.string.chat_theme_caption)
    val current = conversation ?: return
    val override = chatThemes[id]
    val messages by remember(id) { chats.messages(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val deleted = stringResource(app.blesk.feature.chats.R.string.person_chat_deleted)
    val blockedText = stringResource(app.blesk.feature.chats.R.string.person_blocked)
    val username = chats.contact(id)?.username ?: id
    val blocked by remember(chats) { chats.blocked }.collectAsStateWithLifecycle(initialValue = emptyList())
    val all by remember(chats) { chats.conversations }.collectAsStateWithLifecycle(initialValue = emptyList())
    val shared by produceState(emptyList<Conversation>(), all, username) {
        val groups = chats as? GroupController
        value = all.filter { it.kind == ConversationKind.Group || it.kind == ConversationKind.Room }.filter { group ->
            val members = groups?.group(group.id)?.first()?.members?.map { it.username }
            members?.contains(username) ?: group.faces.any { it.username == username }
        }
    }
    PersonProfileScreen(
        conversation = current,
        username = chats.contact(id)?.username ?: id,
        verified = keyState.verified,
        muted = current.muted(System.currentTimeMillis()),
        messages = messages.toImmutableList(),
        actions = PersonActions(
            onWrite = onBack,
            onCall = onCall,
            onMute = { mute -> chats.mute(id, if (mute) Long.MAX_VALUE else 0L) },
            onPin = { chats.setChatPinned(id, it) },
            onTimer = { chats.setTimer(id, it) },
            onVerify = if (keys != null) onVerify else null,
            onTheme = { themeSheet = true },
            onDeleteChat = { forBoth ->
                container.notices.undoable(deleted, hidden = listOf(id)) { chats.deleteChat(id, forBoth) }
                onChatDeleted()
            },
            onOpenMedia = onOpenMedia,
            onOpenFile = { file -> openFile(context, file) },
            onOpenLink = { link -> openLink(context, link) },
            onPlayVoice = { path -> container.audioPlayer.toggle(path) },
            onBlock = if (current.kind == ConversationKind.Direct) {
                { block ->
                    chats.setBlocked(username, block)
                    // Blocked at once, so the chat shows it; «Отменить» takes it back within five seconds.
                    if (block) container.notices.undoable(blockedText, restore = { chats.setBlocked(username, false) }) {}
                }
            } else {
                null
            },
            blocked = blocked.any { it.username == username },
            onOpenConversation = onOpenConversation,
        ),
        onBack = onBack,
        shared = shared.toImmutableList(),
        about = chats.contact(id),
    )
    if (themeSheet) {
        ChatThemeSheet(
            current = override ?: appearance.theme,
            overridden = override != null,
            onSelect = { spec -> scope.launch { container.settings.setChatTheme(id, spec) } },
            onSend = {
                chats.sendTheme(id, override ?: appearance.theme, caption)
                themeSheet = false
            },
            onDismiss = { themeSheet = false },
        )
    }
}

/** Every chat you can write to, room channels included, except the one the message is from. */
@Composable
internal fun ForwardRoute(container: AppContainer, from: String, messageIds: List<String>, onDone: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val notify = rememberNotify()
    val all by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    val targets = all.flatMap { conversation ->
        when {
            conversation.kind == ConversationKind.Channel -> emptyList()
            conversation.kind == ConversationKind.Room && conversation.channels.isNotEmpty() ->
                conversation.channels.filter { it.kind == RoomChannelKind.Text }.map { channel ->
                    conversation.copy(id = channel.id, title = "${conversation.title} · ${channel.title}")
                }
            else -> listOf(conversation)
        }
    }.filter { it.id != from && !locks.hides(it.id, it.archived) { rules -> rules.share } }
    val done = context.getString(R.string.forward_done)
    ForwardScreen(
        conversations = targets.toImmutableList(),
        onSend = { picked ->
            messageIds.forEach { chats.forward(from, it, picked) }
            notify(done)
            onDone()
        },
        onBack = onBack,
    )
}

/**
 * Something shared into blesk from another app: pick chats, «Избранное» first, and it goes to
 * each of them. Files are encrypted into the vault on the way, like anything attached here.
 */
@Composable
internal fun ShareRoute(container: AppContainer, onDone: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val shared = container.shareRequest.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val notify = rememberNotify()
    val scope = rememberCoroutineScope()
    val all by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    val targets = all.filter { it.kind != ConversationKind.Channel && it.kind != ConversationKind.Room && !it.request }
        .filterNot { locks.hides(it.id, it.archived) { rules -> rules.share } }
        .sortedByDescending { it.kind == ConversationKind.Saved }
    val done = context.getString(R.string.share_done)
    val failed = context.getString(R.string.media_failed)
    BackHandler(onBack = onDone)
    ForwardScreen(
        conversations = targets.toImmutableList(),
        onSend = { picked ->
            scope.launch {
                val attachments = shared.files.map { uri ->
                    val mime = context.contentResolver.getType(uri)
                    if (mime?.startsWith("image/") == true || mime?.startsWith("video/") == true) {
                        importVisual(container, mime, uri)
                    } else {
                        container.media.importFile(uri)
                    }
                } + shared.attachments
                picked.forEach { id ->
                    shared.text?.let { chats.send(id, it) }
                    attachments.filterNotNull().forEach { chats.sendAttachment(id, it) }
                }
                notify(if (attachments.any { it == null }) failed else done)
                onDone()
            }
        },
        onBack = onDone,
    )
}
