package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.GroupInfoScreen
import app.blesk.feature.chats.groups.NewGroupScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

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
internal fun SpaceInfoRoute(container: AppContainer, handle: String, onLeft: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val spaces = chats as? SpaceController ?: return
    val me = (chats as? GroupController)?.me.orEmpty()
    val space by remember(handle) { spaces.space(SPACE_PREFIX + handle) }.collectAsStateWithLifecycle(initialValue = null)
    var version by remember { mutableIntStateOf(0) }
    val members by produceState<List<SpaceMember>?>(null, handle, version, space?.role) {
        val role = space?.role
        value = if (role == SpaceRole.Owner || role == SpaceRole.Admin) spaces.spaceMembers(handle) else emptyList()
    }
    val current = space ?: return
    SpaceInfoScreen(
        space = current,
        me = me,
        members = members?.toImmutableList(),
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
}

private const val SPACE_PREFIX = "space:"

@Composable
internal fun GroupInfoRoute(container: AppContainer, groupId: String, onLeft: () -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val state by remember(groupId) { groups.group(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val conversation by remember(groupId) { chats.conversation(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val leftText = stringResource(app.blesk.feature.chats.R.string.group_left)
    val current = state ?: return
    GroupInfoScreen(
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
}
