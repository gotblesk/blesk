package app.blesk.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.KeyState
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.feature.chats.ChatInfoScreen
import app.blesk.feature.chats.ForwardScreen
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf

@Composable
internal fun ChatInfoRoute(container: AppContainer, id: String, onVerify: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val conversation by remember(id) { chats.conversation(id) }.collectAsStateWithLifecycle(initialValue = null)
    val keys = chats as? KeyVerification
    val keyState by remember(id, keys) { keys?.keyState(id) ?: flowOf(KeyState(false, false)) }
        .collectAsStateWithLifecycle(initialValue = KeyState(false, false))
    val current = conversation ?: return
    ChatInfoScreen(
        conversation = current,
        username = chats.contact(id)?.username ?: id,
        verified = keyState.verified,
        onVerify = if (keys != null) onVerify else null,
        onTimer = { chats.setTimer(id, it) },
        onPin = { chats.setChatPinned(id, it) },
        onBack = onBack,
    )
}

/** Every chat you can write to, room channels included, except the one the message is from. */
@Composable
internal fun ForwardRoute(container: AppContainer, from: String, messageId: String, onDone: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val all by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val targets = all.flatMap { conversation ->
        when {
            conversation.kind == ConversationKind.Channel -> emptyList()
            conversation.kind == ConversationKind.Room && conversation.channels.isNotEmpty() ->
                conversation.channels.filter { it.kind == RoomChannelKind.Text }.map { channel ->
                    conversation.copy(id = channel.id, title = "${conversation.title} · ${channel.title}")
                }
            else -> listOf(conversation)
        }
    }.filter { it.id != from }
    val done = context.getString(R.string.forward_done)
    ForwardScreen(
        conversations = targets.toImmutableList(),
        onSend = { picked ->
            chats.forward(from, messageId, picked)
            Toast.makeText(context, done, Toast.LENGTH_SHORT).show()
            onDone()
        },
        onBack = onBack,
    )
}
