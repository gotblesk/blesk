package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.calls.CallActivity
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.chats.ArchiveActions
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.GroupInfoExtras
import app.blesk.feature.chats.groups.GroupInfoScreen
import app.blesk.feature.chats.groups.NewGroupScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant

@Composable
internal fun NewGroupRoute(container: AppContainer, kind: NewKind, onCreated: (String) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val spaces = chats as? SpaceController
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    NewGroupScreen(
        contacts = contacts.toImmutableList(),
        onCreate = { spec ->
            when (spec.kind) {
                NewKind.Group, NewKind.Room -> {
                    val kind = if (spec.kind == NewKind.Room) GroupKind.Room else GroupKind.Group
                    onCreated(groups.createGroup(kind, spec.title, spec.members))
                    SpaceResult.Ok
                }
                NewKind.Channel, NewKind.OpenRoom -> {
                    val kind = if (spec.kind == NewKind.Channel) SpaceKind.Channel else SpaceKind.Room
                    val (result, id) = spaces?.createSpace(kind, spec.handle, spec.title, spec.about) ?: (SpaceResult.Failed to null)
                    id?.let(onCreated)
                    result
                }
            }
        },
        onBack = onBack,
        initialKind = kind,
    )
}

@Composable
internal fun SpaceInfoRoute(
    container: AppContainer,
    handle: String,
    onLeft: () -> Unit,
    onBack: () -> Unit,
    onOpenMedia: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val spaces = chats as? SpaceController ?: return
    val me = (chats as? GroupController)?.me.orEmpty()
    val space by remember(handle) { spaces.space(SPACE_PREFIX + handle) }.collectAsStateWithLifecycle(initialValue = null)
    var version by remember { mutableIntStateOf(0) }
    var membersFailed by remember { mutableStateOf(false) }
    val members by produceState<List<SpaceMember>?>(null, handle, version, space?.role) {
        value = null
        membersFailed = false
        val role = space?.role
        val loaded = if (role == SpaceRole.Owner || role == SpaceRole.Admin) spaces.spaceMembers(handle) else emptyList()
        membersFailed = loaded == null
        value = loaded
    }
    val context = LocalContext.current
    val ids = remember(space) {
        listOf(SPACE_PREFIX + handle) + space?.channels.orEmpty().map { "$SPACE_PREFIX$handle:${it.id}" }
    }
    val messages by remember(ids) {
        combine(ids.map { chats.messages(it) }) { lists -> lists.toList().flatten().sortedBy { it.sentAt } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val current = space ?: return
    var themeSheet by remember { mutableStateOf(false) }
    SpaceInfoScreen(
        onTheme = { themeSheet = true },
        messages = messages.toImmutableList(),
        archive = ArchiveActions(
            onOpenMedia = { id -> messages.firstOrNull { it.id == id }?.let { onOpenMedia(it.conversationId, id) } },
            onOpenFile = { file -> openFile(context, file) },
            onOpenLink = { link -> openLink(context, link) },
            onPlayVoice = { path -> container.audioPlayer.toggle(path) },
        ),
        space = current,
        me = me,
        members = members?.toImmutableList(),
        membersFailed = membersFailed,
        onRetryMembers = { version++ },
        nameOf = { username -> chats.contact(username)?.name ?: username },
        actions = SpaceActions(
            update = { title, about -> spaces.updateSpace(handle, title, about) },
            setRole = { username, role ->
                spaces.setSpaceRole(handle, username, role)
                version++
            },
            ban = { username ->
                spaces.ban(handle, username)
                version++
            },
            addChannel = { spaces.addSpaceChannel(handle, it) },
            removeChannel = { spaces.removeSpaceChannel(handle, it) },
            leave = {
                spaces.leaveSpace(handle)
                onLeft()
            },
            delete = {
                if (spaces.deleteSpace(handle)) onLeft()
            },
        ),
        onBack = onBack,
    )
    if (themeSheet) ConversationThemeSheet(container, ids, sendTo = null, onDismiss = { themeSheet = false })
}

private const val SPACE_PREFIX = "space:"

@Composable
internal fun GroupInfoRoute(
    container: AppContainer,
    groupId: String,
    onLeft: () -> Unit,
    onBack: () -> Unit,
    onOpenPerson: (username: String) -> Unit = {},
    onOpenMedia: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val state by remember(groupId) { groups.group(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val conversation by remember(groupId) { chats.conversation(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val leftText = stringResource(app.blesk.feature.chats.R.string.group_left)
    val context = LocalContext.current
    val notify = rememberNotify()
    val callsLater = stringResource(app.blesk.feature.chats.R.string.group_calls_later)
    // A room keeps its messages in its text channels; the archive gathers them all.
    val conversationIds = remember(state) {
        listOf(groupId) + state?.channels.orEmpty().filter { it.kind == GroupChannelKind.Text }.map { "$groupId:${it.id}" }
    }
    val messages by remember(conversationIds) {
        combine(conversationIds.map { chats.messages(it) }) { lists -> lists.toList().flatten().sortedBy { it.sentAt } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val presence by chats.presence.collectAsStateWithLifecycle()
    val online = remember(presence) {
        val now = Instant.now()
        presence.filterValues { it.isOnline(now) }.keys.map { chats.contact(it)?.username ?: it }.toImmutableSet()
    }
    var themeSheet by remember { mutableStateOf(false) }
    val current = state ?: return
    val extras = GroupInfoExtras(
        onTheme = { themeSheet = true },
        messages = messages.toImmutableList(),
        online = online,
        muted = conversation?.muted(System.currentTimeMillis()) == true,
        onMute = { mute -> chats.mute(groupId, if (mute) Long.MAX_VALUE else 0L) },
        onCall = {
            // A room talks in its voice channels; a group has no call of its own yet.
            val voice = current.channels.firstOrNull { it.kind == GroupChannelKind.Voice }
            if (voice != null) {
                context.startActivity(CallActivity.voice(context, groupId, voice.id, voice.id, "${current.title} · ${voice.title}"))
            } else {
                notify(callsLater)
            }
        },
        onOpenPerson = onOpenPerson,
        archive = ArchiveActions(
            onOpenMedia = { id -> messages.firstOrNull { it.id == id }?.let { onOpenMedia(it.conversationId, id) } },
            onOpenFile = { file -> openFile(context, file) },
            onOpenLink = { link -> openLink(context, link) },
            onPlayVoice = { path -> container.audioPlayer.toggle(path) },
        ),
    )
    GroupInfoScreen(
        extras = extras,
        timerSeconds = conversation?.timerSeconds ?: 0,
        onTimer = { chats.setTimer(groupId, it) },
        state = current,
        me = groups.me,
        contacts = contacts.toImmutableList(),
        nameOf = { username -> chats.contact(username)?.name ?: username },
        actions = GroupActions(
            rename = { groups.rename(groupId, it) },
            addMembers = { groups.addMembers(groupId, it) },
            removeMember = { groups.removeMember(groupId, it) },
            setRole = { username, role -> groups.setRole(groupId, username, role) },
            addChannel = { title, kind, adminsOnly -> groups.addChannel(groupId, title, kind, adminsOnly) },
            removeChannel = { groups.removeChannel(groupId, it) },
            setInvite = { groups.setInvite(groupId, it) },
            // The group leaves the list at once; the leaving itself waits out «Отменить».
            leave = {
                container.notices.undoable(leftText, hidden = listOf(groupId)) {
                    container.scope.launch { groups.leave(groupId) }
                }
                onLeft()
            },
        ),
        onBack = onBack,
    )
    if (themeSheet) {
        ConversationThemeSheet(container, conversationIds, sendTo = groupId, onDismiss = { themeSheet = false })
    }
}
