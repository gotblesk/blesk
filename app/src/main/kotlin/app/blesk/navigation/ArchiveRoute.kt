package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMenu
import app.blesk.feature.chats.EmptyChats
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import java.time.Instant

@Composable
internal fun ArchiveRoute(container: AppContainer, onOpenConversation: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by chats.typing.collectAsStateWithLifecycle()
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val archived = conversations.filter { it.archived }
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
        )
    }
    menuFor?.let { conversation ->
        ChatMenu(conversation, System.currentTimeMillis(), chatMenuActions(chats, conversation), onDismiss = { menuFor = null })
    }
}
