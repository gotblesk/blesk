package app.blesk.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMenu
import app.blesk.feature.chats.EmptyChats
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun ArchiveRoute(container: AppContainer, onOpenConversation: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by chats.typing.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val archived = conversations.filter { it.archived }
    val keepArchived by container.settings.keepArchived.collectAsStateWithLifecycle(initialValue = false)
    val folded by container.settings.archiveFolded.collectAsStateWithLifecycle(initialValue = false)
    val scope = rememberCoroutineScope()
    val onPinLimit = rememberPinLimit()
    LensScaffold(top = { TopLens(title = stringResource(R.string.chats_archive), onBack = onBack) }) { padding ->
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
            onSwipeArchive = { chats.setArchived(it.id, false) },
            onSwipeRead = { toggleRead(chats, it) },
            onReorderPinned = chats::reorderPinned,
            onSwipeUnpin = { chats.setChatPinned(it.id, false) },
            header = {
                item(key = "options") {
                    ArchiveOptions(
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
        val actions = chatMenuActions(chats, conversation, conversations, onPinLimit)
        ChatMenu(conversation, System.currentTimeMillis(), actions, onDismiss = { menuFor = null })
    }
}

@Composable
private fun ArchiveOptions(keepArchived: Boolean, onKeepArchived: (Boolean) -> Unit, folded: Boolean, onFolded: (Boolean) -> Unit) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 12.dp)) {
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
