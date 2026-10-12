package app.blesk.navigation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.settings.AppMode
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.feature.chats.NewChatActions
import app.blesk.feature.chats.NewChatScreen
import app.blesk.feature.chats.R
import app.blesk.feature.chats.RoomsAddScreen
import app.blesk.feature.chats.SpaceSearch
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.profile.profileLink
import app.blesk.feature.settings.QrScanner
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.ProfileLink
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun NewChatRoute(
    container: AppContainer,
    onOpenConversation: (String) -> Unit,
    onScan: () -> Unit,
    onNewGroup: (NewKind) -> Unit,
    onBack: () -> Unit,
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val context = LocalContext.current
    val notify = rememberNotify()
    val joinSent = stringResource(R.string.contacts_join_sent)
    val scope = rememberCoroutineScope()
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val server = mode as? AppMode.Server
    NewChatScreen(
        contacts = contacts.toImmutableList(),
        actions = NewChatActions(
            onScan = onScan,
            onNewGroup = if (chats is GroupController) ({ onNewGroup(NewKind.Group) }) else null,
            onNewChannel = if (chats is SpaceController) ({ onNewGroup(NewKind.Channel) }) else null,
            onInvite = server?.let { { scope.launch { invite(context, it.username, chats.linkKey()) } } },
            onOpenContact = { contact -> scope.launch { chats.openDirect(contact.username)?.let(onOpenConversation) } },
            onFind = if (server == null) null else { text -> find(chats, text, onOpenConversation) { notify(joinSent) } },
            spaces = (chats as? SpaceController)?.let { controller ->
                SpaceSearch(
                    search = { query -> if (InviteLink.parse(query) != null) emptyList() else controller.searchSpaces(query) },
                    join = { handle -> controller.joinSpace(handle)?.let(onOpenConversation) },
                )
            },
        ),
        onBack = onBack,
    )
}

@Composable
internal fun RoomsAddRoute(container: AppContainer, onCreate: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    RoomsAddScreen(
        onCreate = onCreate,
        onJoin = { link -> InviteLink.parse(link) != null && (chats as? GroupController)?.join(link) == true },
        onBack = onBack,
    )
}

/** Reads a profile code (`blesk://u/name`, see [ProfileLink]) or a group invite and acts on it. */
@Composable
internal fun ScanRoute(container: AppContainer, onOpenConversation: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val notify = rememberNotify()
    val joinSent = stringResource(R.string.contacts_join_sent)
    val scope = rememberCoroutineScope()
    LensScaffold(top = { TopLens(title = context.getString(R.string.new_chat_scan), onBack = onBack) }) { padding ->
        QrScanner(
            onCode = { code ->
                scope.launch {
                    val handled = when {
                        ProfileLink.parse(code) != null -> {
                            val link = ProfileLink.parse(code)
                            link?.let { chats.openDirect(it.username, it.key) }?.let {
                                onOpenConversation(it)
                                true
                            } ?: false
                        }
                        InviteLink.parse(code) != null -> find(chats, code, onOpenConversation) { notify(joinSent) }
                        else -> false
                    }
                    if (!handled) notify(context.getString(R.string.scan_unknown))
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/** A username opens a chat; an invite link asks the group's admin to let you in. */
internal suspend fun find(chats: ChatRepository, text: String, open: (String) -> Unit, onJoinSent: () -> Unit): Boolean {
    if (InviteLink.parse(text) != null) {
        val sent = (chats as? GroupController)?.join(text) == true
        if (sent) onJoinSent()
        return sent
    }
    ProfileLink.parse(text)?.let { link -> return chats.openDirect(link.username, link.key)?.also(open) != null }
    return chats.openDirect(text.removePrefix("@").lowercase())?.also(open) != null
}

internal fun invite(context: Context, username: String, key: String?) {
    val text = context.getString(R.string.new_chat_invite_text, profileLink(username, key))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
