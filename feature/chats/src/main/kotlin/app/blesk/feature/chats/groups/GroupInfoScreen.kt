package app.blesk.feature.chats.groups

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.GroupAvatar
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.LocalClock
import app.blesk.feature.chats.Archive
import app.blesk.feature.chats.ArchiveActions
import app.blesk.feature.chats.ArchiveKind
import app.blesk.feature.chats.EncryptionRow
import app.blesk.feature.chats.MenuSheet
import app.blesk.feature.chats.R
import app.blesk.feature.chats.TimerSection
import app.blesk.feature.chats.locks.ChatLockEntry
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

/** Everything an admin can do to a group; members only see and leave. */
class GroupActions(
    val addMembers: suspend (List<String>) -> Unit,
    val addChannel: suspend (String, GroupChannelKind, Boolean) -> Unit,
    val removeChannel: suspend (String) -> Unit,
    /** Any change checked by the group's rules; false when it was not allowed. */
    val change: suspend (GroupChange) -> Boolean,
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
    /** The link that carries an invite code; null without a server, where no link leads anywhere. */
    val inviteLink: ((code: String) -> String)? = { it },
    /** The code or a fingerprint before handing over or deleting; null when the app has no lock. */
    val gate: IdentityGate? = null,
    /** The group is gone: deleted for everyone. */
    val onGone: () -> Unit = {},
    /** Opens in edit mode, as a room does right after it is made. */
    val startEditing: Boolean = false,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    /** «Закрепить» and «В архив» under «Ещё»; a room's long press in the list opens these details instead of a menu. */
    val onPin: ((Boolean) -> Unit)? = null,
    val onArchive: ((Boolean) -> Unit)? = null,
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

private enum class Sheet { More, Add, Timer }

/**
 * About a group or a closed room, by a tap on its header: who is in it and who of them is
 * online, the action cluster, then tabs — for a group its members and what was sent in it, for
 * a room its channels, members, roles and media. Like a profile, those who may change it press
 * «Изменить» and change it in place; below, «Управление» holds the rest.
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
    val now = LocalClock.current().toEpochMilli()
    val room = state.kind == GroupKind.Room
    fun run(block: suspend () -> Unit) {
        scope.launch { block() }
    }
    val refused = stringResource(R.string.manage_refused)
    val env = ManageEnv(
        state = state,
        me = me,
        nameOf = nameOf,
        change = actions.change,
        run = ::run,
        inviteLink = extras.inviteLink,
        gate = extras.gate,
        onOpenPerson = extras.onOpenPerson,
        onGone = extras.onGone,
        refused = { notify(refused) },
    )
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var page by rememberSaveable { mutableStateOf<ManagePage?>(null) }
    var editing by rememberSaveable { mutableStateOf(extras.startEditing && env.can(Right.EditInfo)) }
    var saves by remember { mutableIntStateOf(0) }
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
    val draft = rememberEditDraft(state)

    Box(modifier) {
        ScrollScreen(
            title = state.title,
            onBack = onBack,
            bottom = {
                SaveCapsule(visible = editing, dirty = draft.dirty(state)) {
                    val change = draft.toChange()
                    run {
                        if (actions.change(change)) saves++
                    }
                    editing = false
                }
            },
        ) {
            Box(Modifier.flashOn(saves, RoundedCornerShape(28.dp))) {
                if (editing) {
                    EditHero(draft, hint = if (room && extras.startEditing) stringResource(R.string.manage_edit_hint_room) else null)
                } else {
                    InfoHero(
                        title = state.title,
                        lines = listOf(
                            pluralStringResource(R.plurals.chats_members, state.members.size, state.members.size) +
                                if (onlineCount > 0) " · " + pluralStringResource(R.plurals.group_online, onlineCount, onlineCount) else "",
                            state.description,
                        ),
                        avatar = { GroupAvatar(others.map { nameOf(it.username) to it.username }, size = HeroAvatar) },
                        tag = { state.hue?.let { HueDot(it, size = 10.dp) } },
                    )
                }
            }
            ActionCluster(
                buildList {
                    add(ClusterAction(BleskIcons.Phone, stringResource(R.string.chat_call), extras.onCall))
                    add(
                        ClusterAction(
                            if (extras.muted) BleskIcons.Bell else BleskIcons.BellOff,
                            stringResource(if (extras.muted) R.string.person_unmute else R.string.person_mute),
                        ) { extras.onMute(!extras.muted) },
                    )
                    if (env.can(Right.EditInfo)) {
                        add(ClusterAction(BleskIcons.Edit, stringResource(R.string.group_edit)) { editing = !editing })
                    } else {
                        add(
                            ClusterAction(BleskIcons.Search, stringResource(R.string.search_title)) {
                                searching = !searching
                                if (!searching) query.clearText()
                            },
                        )
                    }
                    add(
                        ClusterAction(BleskIcons.Invite, stringResource(R.string.group_invite_short)) {
                            if (env.can(Right.Invite)) page = ManagePage.Invites else notify(adminsOnlyInvite)
                        },
                    )
                    add(ClusterAction(BleskIcons.More, stringResource(R.string.group_more)) { sheet = Sheet.More })
                },
                Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
            )
            EncryptionRow(protected = true)
            if (searching) TextInput(label = stringResource(R.string.group_search), state = query)
            InfoTabs(tabs.map { stringResource(it.label) }.toImmutableList(), tab.coerceAtMost(tabs.lastIndex)) { tab = it }
            val needle = query.text.toString().trim()
            when (val shown = tabs[tab.coerceAtMost(tabs.lastIndex)]) {
                GroupTab.Channels -> ChannelsSection(env, actions)
                GroupTab.Members -> MemberList(env, needle, extras.online)
                GroupTab.Roles -> RolesSection(env) { page = ManagePage.Roles }
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
            ManageSection(env, now) { page = it }
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
            env = env,
            contacts = contacts,
            actions = actions,
            timerSeconds = timerSeconds,
            onTimer = onTimer,
            onTheme = extras.onTheme,
            extras = extras,
        )
        page?.let { ManageScreen(it, env, now, onBack = { page = null }) }
    }
}

@Composable
private fun GroupSheets(
    sheet: Sheet?,
    onSheet: (Sheet?) -> Unit,
    env: ManageEnv,
    contacts: ImmutableList<Contact>,
    actions: GroupActions,
    timerSeconds: Int,
    onTimer: ((Int) -> Unit)?,
    onTheme: (() -> Unit)?,
    extras: GroupInfoExtras,
) {
    val close = { onSheet(null) }
    val state = env.state
    when (sheet) {
        null -> Unit
        Sheet.More -> MenuSheet(title = state.title, onDismiss = close) {
            Card {
                if (env.can(Right.Invite)) {
                    ListRow(title = stringResource(R.string.group_add_members), icon = BleskIcons.Invite, onClick = { onSheet(Sheet.Add) })
                }
                if (onTimer != null) {
                    ListRow(title = stringResource(R.string.info_timer), icon = BleskIcons.Timer, onClick = { onSheet(Sheet.Timer) })
                }
                ChatLockEntry()
                extras.onPin?.let { pin ->
                    ListRow(
                        title = stringResource(if (extras.pinned) R.string.chats_unpin else R.string.chats_pin),
                        icon = BleskIcons.Pin,
                        showChevron = false,
                        onClick = {
                            close()
                            pin(!extras.pinned)
                        },
                    )
                }
                extras.onArchive?.let { archive ->
                    ListRow(
                        title = stringResource(if (extras.archived) R.string.chats_from_archive else R.string.chats_to_archive),
                        icon = BleskIcons.Archive,
                        showChevron = false,
                        onClick = {
                            close()
                            archive(!extras.archived)
                        },
                    )
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
                        env.run { actions.leave() }
                    },
                )
            }
        }
        Sheet.Add -> MenuSheet(title = stringResource(R.string.group_add_members), onDismiss = close) {
            val candidates = contacts
                .filter { contact -> state.members.none { it.username == contact.username } && contact.username !in state.banned }
                .toImmutableList()
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
                    if (chosen.isNotEmpty()) env.run { actions.addMembers(chosen) }
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

/** Owner and admins, then the room's roles in order with their hue and how many hold them. */
@Composable
private fun RolesSection(env: ManageEnv, onManage: () -> Unit) {
    val state = env.state
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        ListRow(
            title = "${stringResource(R.string.role_owner)} · 1",
            subtitle = stringResource(R.string.role_owner_rights),
            icon = BleskIcons.Shield,
            showChevron = false,
        )
        ListRow(
            title = "${stringResource(R.string.role_admins)} · ${state.members.count { it.role == GroupRole.Admin }}",
            subtitle = stringResource(R.string.role_admin_rights),
            icon = BleskIcons.Shield,
            showChevron = false,
        )
        state.roles.forEach { role ->
            val count = state.members.count { role.id in it.roles }
            ListRow(
                title = role.title,
                subtitle = pluralStringResource(R.plurals.manage_role_members, count, count),
                showChevron = false,
                trailing = { HueDot(role.hue, size = 12.dp) },
            )
        }
        ListRow(
            title = "${stringResource(R.string.role_members)} · ${state.members.count { it.role == GroupRole.Member }}",
            subtitle = stringResource(R.string.role_member_rights),
            icon = BleskIcons.People,
            showChevron = false,
        )
    }
    if (env.can(Right.Roles)) {
        Button(
            text = stringResource(R.string.manage_roles),
            icon = BleskIcons.Edit,
            kind = ButtonKind.Secondary,
            onClick = onManage,
            modifier = Modifier.padding(Blesk.dimens.gutter),
        )
    }
}

private val HeroAvatar = 88.dp
