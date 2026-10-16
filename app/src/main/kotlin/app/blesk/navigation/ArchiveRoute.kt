package app.blesk.navigation

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatRowPlaces
import app.blesk.feature.chats.EmptyChats
import app.blesk.feature.chats.LocalChatLocks
import app.blesk.feature.chats.R
import app.blesk.feature.chats.RequestActions
import app.blesk.feature.chats.RequestsScreen
import app.blesk.locks.setLocked
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ArchiveRoute(container: AppContainer, onOpenConversation: (String) -> Unit, onBack: () -> Unit, onNeedCode: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by chats.typing.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val rowPlaces = remember { ChatRowPlaces() }
    // Under a chat's menu the list is darkened and softly blurred; the lifted row stays sharp.
    val menuBlur by animateDpAsState(if (menuFor != null) MenuBlur else 0.dp, Blesk.motion.settle(), label = "menuBlur")
    val archived = conversations.filter { it.archived }
    val keepArchived by container.settings.keepArchived.collectAsStateWithLifecycle(initialValue = false)
    val folded by container.settings.archiveFolded.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val onPinLimit = rememberPinLimit()
    LensScaffold(
        modifier = Modifier.blur(menuBlur),
        top = { TopLens(title = stringResource(R.string.chats_archive), onBack = onBack) },
    ) { padding ->
        if (archived.isEmpty()) {
            EmptyChats(padding, title = stringResource(R.string.archive_empty_title), text = stringResource(R.string.archive_empty_text))
            return@LensScaffold
        }
        ChatList(
            conversations = archived.toImmutableList(),
            typing = typing.toImmutableSet(),
            now = remember(conversations) { Instant.now() },
            contentPadding = padding,
            onOpenConversation = onOpenConversation,
            onOpenChannel = { _, _ -> },
            onLongPress = { menuFor = it },
            places = rowPlaces,
            onSwipeArchive = { chats.setArchived(it.id, false) },
            onSwipeRead = { toggleRead(chats, it) },
            onReorderPinned = chats::reorderPinned,
            onSwipeUnpin = { chats.setChatPinned(it.id, false) },
            header = {
                item(key = "options") {
                    ArchiveOptions(
                        locked = LocalChatLocks.current.isLocked(ChatLocks.ARCHIVE),
                        onLocked = { lock -> container.setLocked(ChatLocks.ARCHIVE, lock, onNeedCode) },
                        keepArchived = keepArchived,
                        onKeepArchived = { scope.launch { container.settings.setKeepArchived(it) } },
                        folded = folded,
                        onFolded = { scope.launch { container.settings.setArchiveFolded(it) } },
                    )
                }
            },
        )
    }
    menuFor?.let { conversation ->
        ConversationMenu(
            container = container,
            chats = chats,
            conversation = conversation,
            all = conversations,
            anchor = rowPlaces[conversation.id],
            onPinLimit = onPinLimit,
            onOpen = onOpenConversation,
            onDismiss = { menuFor = null },
            onNeedCode = onNeedCode,
        )
    }
}

@Composable
private fun ArchiveOptions(
    locked: Boolean,
    onLocked: (Boolean) -> Unit,
    keepArchived: Boolean,
    onKeepArchived: (Boolean) -> Unit,
    folded: Boolean,
    onFolded: (Boolean) -> Unit,
) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 12.dp)) {
        ToggleRow(
            title = stringResource(R.string.lock_archive),
            subtitle = stringResource(R.string.lock_archive_hint),
            icon = BleskIcons.Lock,
            checked = locked,
            onCheckedChange = onLocked,
        )
        ToggleRow(
            title = stringResource(R.string.chats_keep_archived),
            subtitle = stringResource(R.string.chats_keep_archived_hint),
            checked = keepArchived,
            onCheckedChange = onKeepArchived,
            icon = BleskIcons.Archive,
        )
        ToggleRow(
            title = stringResource(R.string.chats_archive_unfold),
            checked = !folded,
            onCheckedChange = { onFolded(!it) },
            icon = BleskIcons.ArrowDown,
        )
    }
}

@Composable
internal fun RequestsRoute(container: AppContainer, onOpenConversation: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val requests = conversations.filter { it.request && it.id !in container.notices.hidden }
    RequestsScreen(
        requests = requests.toImmutableList(),
        actions = requestActions(container, chats, onAccepted = onOpenConversation),
        onBack = onBack,
    )
}

/** Accepting opens the chat; deleting can be taken back for a few seconds; blocking is silent. */
@Composable
internal fun requestActions(
    container: AppContainer,
    chats: ChatRepository,
    onAccepted: (String) -> Unit = {},
    onDone: () -> Unit = {},
): RequestActions {
    val deleted = stringResource(R.string.person_chat_deleted)
    return RequestActions(
        onAccept = { request ->
            chats.accept(chats.contact(request.id)?.username ?: request.id)
            onAccepted(request.id)
        },
        onDelete = { request ->
            container.notices.undoable(deleted, hidden = listOf(request.id)) { chats.deleteChat(request.id) }
            onDone()
        },
        onBlock = { request ->
            chats.setBlocked(chats.contact(request.id)?.username ?: request.id, true)
            onDone()
        },
        onOpen = { onAccepted(it.id) },
    )
}

private val MenuBlur = 12.dp
