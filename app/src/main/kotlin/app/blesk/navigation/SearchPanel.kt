package app.blesk.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.chats.R
import app.blesk.feature.chats.SearchHit
import app.blesk.feature.chats.SearchResults
import app.blesk.locks.hides
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Results for the search unfolded in the top lens of «Чаты». */
@Composable
internal fun SearchPanel(
    container: AppContainer,
    query: TextFieldState,
    contentPadding: PaddingValues,
    onOpen: (String) -> Unit,
    onOpenMessage: (conversationId: String, messageId: String) -> Unit,
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    var hits by remember { mutableStateOf<ImmutableList<SearchHit>?>(null) }
    val you = stringResource(R.string.chat_you)
    LaunchedEffect(chats) {
        snapshotFlow { query.text.toString().trim() }.collectLatest { needle ->
            if (needle.length < MIN_QUERY) {
                hits = null
                return@collectLatest
            }
            delay(DEBOUNCE_MILLIS)
            hits = chats.search(needle).map { message ->
                val place = chats.conversation(message.conversationId).first()?.title.orEmpty()
                val author = if (message.isOwn) you else chats.contact(message.authorId)?.name ?: message.authorId
                SearchHit(message.conversationId, message.id, place, author, message.text)
            }.toImmutableList()
        }
    }
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val notify = rememberNotify()
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    val archived = remember(conversations) { conversations.filter { it.archived }.map { it.id }.toSet() }
    fun hidden(id: String) = locks.hides(id, id in archived) { it.search }
    val needle = query.text.toString().trim().removePrefix("@").lowercase()
    val matchingChats = if (needle.isEmpty()) {
        emptyList()
    } else {
        conversations.filter { it.title.lowercase().contains(needle) && !hidden(it.id) }
    }
    val matchingPeople = if (needle.isEmpty()) {
        emptyList()
    } else {
        contacts.filter { it.name.lowercase().contains(needle) || it.username.contains(needle) }
            .filter { person -> matchingChats.none { it.id == person.id } && !hidden(person.id) }
    }
    SearchResults(
        query = query.text.toString(),
        hits = hits?.filterNot { hidden(it.conversationId) }?.toImmutableList(),
        onOpen = { onOpenMessage(it.conversationId, it.messageId) },
        contentPadding = contentPadding,
        chats = matchingChats.take(MAX_ROWS).toImmutableList(),
        people = matchingPeople.take(MAX_ROWS).toImmutableList(),
        onOpenChat = { chat -> onOpen(matchingChats.firstOrNull { it.id == chat }?.chatTarget() ?: chat) },
        onOpenPerson = { person -> scope.launch { chats.openDirect(person.username)?.let(onOpen) } },
        onFindUsername = if (mode is AppMode.Server) {
            { username ->
                scope.launch {
                    val id = chats.openDirect(username)
                    if (id != null) onOpen(id) else notify(context.getString(R.string.contacts_not_found))
                }
            }
        } else {
            null
        },
    )
}

private const val MIN_QUERY = 2
private const val MAX_ROWS = 5
private const val DEBOUNCE_MILLIS = 250L
