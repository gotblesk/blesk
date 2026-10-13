package app.blesk.feature.chats.groups

import android.content.ClipData
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.GroupAvatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.Archive
import app.blesk.feature.chats.ArchiveActions
import app.blesk.feature.chats.ArchiveKind
import app.blesk.feature.chats.EncryptionRow
import app.blesk.feature.chats.MenuSheet
import app.blesk.feature.chats.R
import app.blesk.feature.chats.TimerSection
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InviteLink
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Everything an admin can do to a group; members only see and leave. */
class GroupActions(
    val rename: suspend (String) -> Unit,
    val addMembers: suspend (List<String>) -> Unit,
    val removeMember: suspend (String) -> Unit,
    val setRole: suspend (String, GroupRole) -> Unit,
    val addChannel: suspend (String, GroupChannelKind, Boolean) -> Unit,
    val removeChannel: suspend (String) -> Unit,
    val setInvite: suspend (Boolean) -> Unit,
    val leave: suspend () -> Unit,
)

/** What the info screen shows besides the group itself, and what its buttons do outside it. */
class GroupInfoExtras(
    val messages: ImmutableList<Message> = persistentListOf(),
    /** Usernames of members who are online now. */
    val online: ImmutableSet<String> = persistentSetOf(),
    val muted: Boolean = false,
    val onMute: (Boolean) -> Unit = {},
    val onCall: () -> Unit = {},
    val onOpenPerson: (username: String) -> Unit = {},
    val archive: ArchiveActions = ArchiveActions({}, {}, {}, {}),
    /** «Оформление чата»; the header of the chat opens this screen, so the theme lives under «Ещё». */
    val onTheme: (() -> Unit)? = null,
)

private enum class GroupTab(@param:StringRes val label: Int) {
    Channels(R.string.group_channels),
    Members(R.string.group_members),
    Roles(R.string.group_tab_roles),
    Media(R.string.person_tab_media),
    Files(R.string.person_tab_files),
    Links(R.string.person_tab_links),
    Voice(R.string.person_tab_voice),
}

private enum class Sheet { Invite, More, Edit, Add, Timer }

/**
 * About a group or a closed room, by a tap on its header: who is in it and who of them is
 * online, the action cluster, then tabs — for a group its members and what was sent in it, for
 * a room its channels, members, roles and media. What only admins can do hides behind «Ещё».
 */
@Composable
fun GroupInfoScreen(
    state: GroupState,
    me: String,
    contacts: ImmutableList<Contact>,
    nameOf: (String) -> String,
    actions: GroupActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    timerSeconds: Int = 0,
    onTimer: ((Int) -> Unit)? = null,
    extras: GroupInfoExtras = GroupInfoExtras(),
) {
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val admin = state.isAdmin(me)
    val owner = state.roleOf(me) == GroupRole.Owner
    val room = state.kind == GroupKind.Room
    fun run(block: suspend () -> Unit) {
        scope.launch { block() }
    }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var searching by remember { mutableStateOf(false) }
    val query = rememberTextFieldState()
    val tabs = if (room) {
        listOf(GroupTab.Channels, GroupTab.Members, GroupTab.Roles, GroupTab.Media)
    } else {
        listOf(GroupTab.Members, GroupTab.Media, GroupTab.Files, GroupTab.Links, GroupTab.Voice)
    }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val others = state.members.filter { it.username != me }
    val onlineCount = state.members.count { it.username in extras.online }
    val adminsOnlyInvite = stringResource(R.string.group_invite_admins)

    Box(modifier) {
        ScrollScreen(title = state.title, onBack = onBack) {
            InfoHero(
                title = state.title,
                lines = listOf(
                    pluralStringResource(R.plurals.chats_members, state.members.size, state.members.size) +
                        if (onlineCount > 0) " · " + pluralStringResource(R.plurals.group_online, onlineCount, onlineCount) else "",
                ),
                avatar = { GroupAvatar(others.map { nameOf(it.username) to it.username }, size = HeroAvatar) },
            )
            ActionCluster(
                listOf(
                    ClusterAction(BleskIcons.Phone, stringResource(R.string.chat_call), extras.onCall),
                    ClusterAction(
                        if (extras.muted) BleskIcons.Bell else BleskIcons.BellOff,
                        stringResource(if (extras.muted) R.string.person_unmute else R.string.person_mute),
                    ) { extras.onMute(!extras.muted) },
                    ClusterAction(BleskIcons.Search, stringResource(R.string.search_title)) {
                        searching = !searching
                        if (!searching) query.clearText()
                    },
                    ClusterAction(BleskIcons.Invite, stringResource(R.string.group_invite_short)) {
                        if (admin) sheet = Sheet.Invite else notify(adminsOnlyInvite)
                    },
                    ClusterAction(BleskIcons.More, stringResource(R.string.group_more)) { sheet = Sheet.More },
                ),
                Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
            )
            EncryptionRow(protected = true)
            if (searching) TextInput(label = stringResource(R.string.group_search), state = query)
            InfoTabs(tabs.map { stringResource(it.label) }.toImmutableList(), tab.coerceAtMost(tabs.lastIndex)) { tab = it }
            val needle = query.text.toString().trim()
            when (val shown = tabs[tab.coerceAtMost(tabs.lastIndex)]) {
                GroupTab.Channels -> ChannelsSection(state, admin, actions, ::run)
                GroupTab.Members -> MembersSection(state, me, needle, nameOf, extras, actions, ::run)
                GroupTab.Roles -> RolesSection(state)
                else -> Archive(
                    kind = when (shown) {
                        GroupTab.Files -> ArchiveKind.Files
                        GroupTab.Links -> ArchiveKind.Links
                        GroupTab.Voice -> ArchiveKind.Voice
                        else -> ArchiveKind.Media
                    },
                    messages = extras.messages,
                    actions = extras.archive,
                    query = needle,
                )
            }
            Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = Blesk.dimens.sectionGap)) {
                ListRow(
                    title = stringResource(if (room) R.string.room_leave else R.string.group_leave_full),
                    subtitle = stringResource(R.string.group_leave_hint),
                    icon = BleskIcons.Back,
                    showChevron = false,
                    onClick = { run { actions.leave() } },
                )
            }
        }
        GroupSheets(
            sheet = sheet,
            onSheet = { sheet = it },
            state = state,
            me = me,
            admin = admin,
            contacts = contacts,
            actions = actions,
            run = ::run,
            timerSeconds = timerSeconds,
            onTimer = onTimer,
            onTheme = extras.onTheme,
        )
    }
}

@Composable
private fun GroupSheets(
    sheet: Sheet?,
    onSheet: (Sheet?) -> Unit,
    state: GroupState,
    me: String,
    admin: Boolean,
    contacts: ImmutableList<Contact>,
    actions: GroupActions,
    run: (suspend () -> Unit) -> Unit,
    timerSeconds: Int,
    onTimer: ((Int) -> Unit)?,
    onTheme: (() -> Unit)?,
) {
    val close = { onSheet(null) }
    when (sheet) {
        null -> Unit
        Sheet.Invite -> MenuSheet(title = stringResource(R.string.group_invite), onDismiss = close) {
            InviteSection(state, me, onToggle = { run { actions.setInvite(it) } })
        }
        Sheet.More -> MenuSheet(title = state.title, onDismiss = close) {
            Card {
                if (admin) {
                    ListRow(title = stringResource(R.string.group_edit), icon = BleskIcons.Edit, onClick = { onSheet(Sheet.Edit) })
                    ListRow(title = stringResource(R.string.group_add_members), icon = BleskIcons.Invite, onClick = { onSheet(Sheet.Add) })
                }
                if (onTimer != null) {
                    ListRow(title = stringResource(R.string.info_timer), icon = BleskIcons.Timer, onClick = { onSheet(Sheet.Timer) })
                }
                onTheme?.let { theme ->
                    ListRow(
                        title = stringResource(R.string.chat_theme),
                        icon = BleskIcons.Appearance,
                        onClick = {
                            close()
                            theme()
                        },
                    )
                }
                ListRow(
                    title = stringResource(if (state.kind == GroupKind.Room) R.string.room_leave else R.string.group_leave_full),
                    icon = BleskIcons.Back,
                    showChevron = false,
                    onClick = {
                        close()
                        run { actions.leave() }
                    },
                )
            }
        }
        Sheet.Edit -> MenuSheet(title = stringResource(R.string.group_edit), onDismiss = close) {
            val title = rememberTextFieldState(state.title)
            TextInput(label = stringResource(R.string.group_name), state = title)
            Button(
                text = stringResource(R.string.group_save),
                onClick = {
                    run { actions.rename(title.text.toString()) }
                    close()
                },
                modifier = Modifier.fillMaxWidth().padding(Blesk.dimens.gutter),
            )
        }
        Sheet.Add -> MenuSheet(title = stringResource(R.string.group_add_members), onDismiss = close) {
            val candidates = contacts.filter { contact -> state.members.none { it.username == contact.username } }.toImmutableList()
            if (candidates.isEmpty()) {
                Text(
                    stringResource(R.string.group_everyone_here),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(Blesk.dimens.gutter),
                )
                return@MenuSheet
            }
            val picked = remember { mutableStateListOf<String>() }
            ContactPicker(candidates, picked)
            Button(
                text = stringResource(R.string.group_add),
                onClick = {
                    val chosen = picked.toList()
                    if (chosen.isNotEmpty()) run { actions.addMembers(chosen) }
                    close()
                },
                modifier = Modifier.fillMaxWidth().padding(Blesk.dimens.gutter),
            )
        }
        Sheet.Timer -> MenuSheet(title = stringResource(R.string.info_timer), onDismiss = close) {
            onTimer?.let { TimerSection(timerSeconds, it) }
        }
    }
}

@Composable
private fun MembersSection(
    state: GroupState,
    me: String,
    needle: String,
    nameOf: (String) -> String,
    extras: GroupInfoExtras,
    actions: GroupActions,
    run: (suspend () -> Unit) -> Unit,
) {
    val owner = state.roleOf(me) == GroupRole.Owner
    val you = stringResource(R.string.group_you)
    fun found(username: String) = needle.isEmpty() || nameOf(username).contains(needle, true) || username.contains(needle, true)

    // Admins manage plain members; only the owner manages admins, and nobody manages the owner.
    fun manageable(member: GroupMember) =
        state.isAdmin(me) && member.username != me && member.role != GroupRole.Owner && (owner || member.role == GroupRole.Member)
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        state.members.filter { found(it.username) }.sortedBy { it.role.ordinal }.forEach { member ->
            val mine = member.username == me
            MemberRow(
                name = if (mine) "${nameOf(me)} · $you" else nameOf(member.username),
                username = member.username,
                role = member.role,
                online = member.username in extras.online,
                onOpen = if (mine) null else ({ extras.onOpenPerson(member.username) }),
                manage = MemberManage(
                    canPromote = owner,
                    onSetRole = { role -> run { actions.setRole(member.username, role) } },
                    onRemove = { run { actions.removeMember(member.username) } },
                ).takeIf { manageable(member) },
            )
        }
    }
}

/** What an admin may do to a member, offered on a long press. */
private class MemberManage(val canPromote: Boolean, val onSetRole: (GroupRole) -> Unit, val onRemove: () -> Unit)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MemberRow(name: String, username: String, role: GroupRole, online: Boolean, onOpen: (() -> Unit)?, manage: MemberManage?) {
    var open by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = onOpen != null || manage != null,
                    role = Role.Button,
                    onLongClickLabel = manage?.let { stringResource(R.string.group_manage) },
                    onLongClick = manage?.let {
                        {
                            haptics.menu()
                            open = !open
                        }
                    },
                    onClick = { onOpen?.invoke() },
                )
                .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(name, size = 40.dp, key = username, online = online)
            Column(Modifier.weight(1f)) {
                Text(name, style = Blesk.type.bodyStrong, maxLines = 1)
                Text("@$username", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
            }
            when (role) {
                GroupRole.Owner -> Tag(stringResource(R.string.group_role_owner))
                GroupRole.Admin -> Tag(stringResource(R.string.group_role_admin))
                GroupRole.Member -> Unit
            }
        }
        if (open && manage != null) {
            Row(
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (manage.canPromote) {
                    val promote = role == GroupRole.Member
                    Button(
                        text = stringResource(if (promote) R.string.group_make_admin else R.string.group_make_member),
                        kind = ButtonKind.Secondary,
                        onClick = {
                            open = false
                            manage.onSetRole(if (promote) GroupRole.Admin else GroupRole.Member)
                        },
                    )
                }
                Button(
                    text = stringResource(R.string.group_remove),
                    kind = ButtonKind.Secondary,
                    onClick = {
                        open = false
                        manage.onRemove()
                    },
                )
            }
        }
    }
}

/** Who can do what in a room, and how many hold each role. */
@Composable
private fun RolesSection(state: GroupState) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        listOf(
            Triple(GroupRole.Owner, R.string.role_owner, R.string.role_owner_rights),
            Triple(GroupRole.Admin, R.string.role_admins, R.string.role_admin_rights),
            Triple(GroupRole.Member, R.string.role_members, R.string.role_member_rights),
        ).forEach { (role, title, rights) ->
            val count = state.members.count { it.role == role }
            ListRow(
                title = "${stringResource(title)} · $count",
                subtitle = stringResource(rights),
                icon = BleskIcons.Shield,
                showChevron = false,
            )
        }
    }
}

@Composable
private fun InviteSection(state: GroupState, me: String, onToggle: (Boolean) -> Unit) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.group_invite))
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        ToggleRow(
            title = stringResource(R.string.group_invite),
            subtitle = stringResource(R.string.group_invite_hint),
            checked = state.invite != null,
            onCheckedChange = onToggle,
        )
        state.invite?.let { code ->
            val link = InviteLink.format(state.id, me, code)
            Text(
                text = link,
                style = Blesk.type.caption,
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter),
            )
            Button(
                text = stringResource(if (copied) R.string.group_invite_copied else R.string.group_invite_copy),
                icon = BleskIcons.Copy,
                kind = ButtonKind.Secondary,
                onClick = {
                    scope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("blesk", link)))
                        copied = true
                    }
                },
                modifier = Modifier.padding(Blesk.dimens.gutter),
            )
        }
    }
}

@Composable
private fun ChannelsSection(state: GroupState, admin: Boolean, actions: GroupActions, run: (suspend () -> Unit) -> Unit) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        state.channels.forEach { channel ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon = if (channel.kind == GroupChannelKind.Voice) BleskIcons.Voice else BleskIcons.Hash,
                    contentDescription = null,
                    tint = Blesk.colors.textSecondary,
                    size = 20.dp,
                )
                Text(channel.title, style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
                if (channel.adminsOnly) Tag(stringResource(R.string.group_role_admin))
                if (admin) {
                    IconButton(BleskIcons.Trash, stringResource(R.string.group_channel_delete, channel.title), {
                        run { actions.removeChannel(channel.id) }
                    })
                }
            }
        }
    }
    if (!admin) return
    val title = rememberTextFieldState()
    var voice by remember { mutableStateOf(false) }
    var adminsOnly by remember { mutableStateOf(false) }
    SectionHeader(stringResource(R.string.group_channel_add))
    TextInput(label = stringResource(R.string.group_channel_name), state = title)
    Segmented(
        options = persistentListOf(stringResource(R.string.group_channel_text), stringResource(R.string.group_channel_voice)),
        selected = if (voice) 1 else 0,
        onSelect = { voice = it == 1 },
    )
    ToggleRow(title = stringResource(R.string.group_channel_admins_only), checked = adminsOnly, onCheckedChange = { adminsOnly = it })
    Button(
        text = stringResource(R.string.group_channel_add),
        kind = ButtonKind.Secondary,
        onClick = {
            val name = title.text.toString().trim()
            val kind = if (voice) GroupChannelKind.Voice else GroupChannelKind.Text
            if (name.isNotEmpty()) run { actions.addChannel(name, kind, adminsOnly) }
        },
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter),
    )
}

private val HeroAvatar = 88.dp
