package app.blesk.navigation

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.feature.chats.R
import app.blesk.feature.chats.SearchHit
import app.blesk.feature.chats.SearchScreen
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

@Composable
internal fun SearchRoute(container: AppContainer, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val query = rememberTextFieldState()
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
    SearchScreen(query = query, hits = hits, onOpen = { onOpen(it.conversationId) }, onBack = onBack)
}

private const val MIN_QUERY = 2
private const val DEBOUNCE_MILLIS = 250L
