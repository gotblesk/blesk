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
import app.blesk.core.network.ServerAddress
import app.blesk.feature.chats.NewChatActions
import app.blesk.feature.chats.NewChatScreen
import app.blesk.feature.chats.R
import app.blesk.feature.chats.RoomsAddScreen
import app.blesk.feature.chats.SpaceSearch
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.profile.profileLink
import app.blesk.feature.settings.QrScanner
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
internal fun NewChatRoute(
    container: AppContainer,
    onOpenConversation: (String) -> Unit,
    onScan: () -> Unit,
    onNewGroup: (NewKind) -> Unit,
    /** «Ты → Приглашения» on blesk's own server; elsewhere «Пригласить» sends a link to your profile instead. */
    onOpenInvites: (() -> Unit)?,
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
            onInvite = onOpenInvites ?: server?.let { { scope.launch { invite(context, it.username, chats.linkKey()) } } },
            onOpenContact = { contact -> scope.launch { chats.openDirect(contact.username)?.let(onOpenConversation) } },
            onFind = if (server == null) null else { text -> find(chats, text, onOpenConversation) { notify(joinSent) } },
            spaces = (chats as? SpaceController)?.let { controller ->
                SpaceSearch(
                    search = { query -> spaceQuery(query)?.let { controller.searchSpaces(it) }.orEmpty() },
                    join = { handle -> controller.joinSpace(handle)?.let(onOpenConversation) },
                )
            },
        ),
        onBack = onBack,
    )
}

@Composable
internal fun RoomsAddRoute(container: AppContainer, onCreate: () -> Unit, onOpenConversation: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val notify = rememberNotify()
    val needsServer = stringResource(app.blesk.R.string.call_needs_server)
    RoomsAddScreen(
        onCreate = onCreate,
        onJoin = { link ->
            val target = LinkTarget.of(link)?.takeIf { it is LinkTarget.Group || it is LinkTarget.Space }
            when {
                target == null -> false
                // An invite asks the group's admin on their server; the demo has nobody to ask.
                mode == AppMode.Demo -> null.also { notify(needsServer) }
                else -> when (reach(target, chats, onOpenConversation, onOpenConversation)) {
                    LinkOutcome.JoinRequested -> true
                    LinkOutcome.Opened -> null
                    else -> false
                }
            }
        },
        onBack = onBack,
    )
}

/** Reads a profile code, an invite or a server's code and goes where it points; a profile opens its chat. */
@Composable
internal fun ScanRoute(
    container: AppContainer,
    onOpenConversation: (String) -> Unit,
    onSignUp: () -> Unit,
    onOtherServer: (ServerAddress) -> Unit,
    onBack: () -> Unit,
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val context = LocalContext.current
    val notify = rememberNotify()
    val scope = rememberCoroutineScope()
    val screens = LinkScreens(
        conversation = onOpenConversation,
        profile = onOpenConversation,
        signUp = onSignUp,
        otherServer = onOtherServer,
    )
    LensScaffold(top = { TopLens(title = context.getString(R.string.new_chat_scan), onBack = onBack) }) { padding ->
        QrScanner(
            onCode = { code ->
                scope.launch {
                    val target = LinkTarget.of(code)
                    if (target == null) {
                        notify(context.getString(R.string.scan_unknown))
                        return@launch
                    }
                    val account = chats.takeIf { container.settings.mode.first() is AppMode.Server }
                    follow(target, account, container.pendingInvite, screens).notice?.let { notify(context.getString(it)) }
                }
            },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}

/** A username opens a chat; a link opens what it points at, and a group invite asks the group's admin to let you in. */
internal suspend fun find(chats: ChatRepository, text: String, open: (String) -> Unit, onJoinSent: () -> Unit): Boolean {
    val target = LinkTarget.of(text) ?: return chats.openDirect(text.removePrefix("@").lowercase())?.also(open) != null
    val outcome = reach(target, chats, openConversation = open, openProfile = open)
    if (outcome == LinkOutcome.JoinRequested) onJoinSent()
    return outcome == LinkOutcome.Opened || outcome == LinkOutcome.JoinRequested
}

internal fun invite(context: Context, username: String, key: String?) {
    val text = context.getString(R.string.new_chat_invite_text, profileLink(username, key))
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
