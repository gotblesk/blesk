package app.blesk.feature.chats.groups

import android.content.ClipData
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
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
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InviteLink
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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

@Composable
fun GroupInfoScreen(
    state: GroupState,
    me: String,
    contacts: ImmutableList<Contact>,
    nameOf: (String) -> String,
    actions: GroupActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val admin = state.isAdmin(me)
    val owner = state.roleOf(me) == GroupRole.Owner
    fun run(block: suspend () -> Unit) {
        scope.launch { block() }
    }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)

    ScrollScreen(title = state.title, onBack = onBack, modifier = modifier) {
        if (admin) {
            val title = rememberTextFieldState(state.title)
            TextInput(label = stringResource(R.string.group_name), state = title)
            Button(
                text = stringResource(R.string.group_save),
                kind = ButtonKind.Secondary,
                onClick = { run { actions.rename(title.text.toString()) } },
                modifier = pad,
            )
        }

        SectionHeader(stringResource(R.string.group_members))
        Card(pad) {
            state.members.forEach { member ->
                MemberRow(
                    name = if (member.username == me) "${nameOf(me)} · ${stringResource(R.string.group_you)}" else nameOf(member.username),
                    role = member.role,
                    canManage = admin && member.username != me && member.role != GroupRole.Owner &&
                        (owner || member.role == GroupRole.Member),
                    canPromote = owner,
                    onSetRole = { role -> run { actions.setRole(member.username, role) } },
                    onRemove = { run { actions.removeMember(member.username) } },
                )
            }
        }

        if (admin) {
            val candidates = contacts.filter { contact -> state.members.none { it.username == contact.username } }.toImmutableList()
            if (candidates.isNotEmpty()) {
                SectionHeader(stringResource(R.string.group_add_members))
                val picked = remember { mutableStateListOf<String>() }
                ContactPicker(candidates, picked)
                Button(
                    text = stringResource(R.string.group_add),
                    kind = ButtonKind.Secondary,
                    onClick = {
                        val chosen = picked.toList()
                        picked.clear()
                        if (chosen.isNotEmpty()) run { actions.addMembers(chosen) }
                    },
                    modifier = pad.padding(top = 8.dp),
                )
            }
            InviteSection(state, me, onToggle = { run { actions.setInvite(it) } })
        }

        if (state.kind == GroupKind.Room) {
            ChannelsSection(state, admin, actions, ::run)
        }

        Card(pad.padding(top = Blesk.dimens.sectionGap)) {
            ListRow(
                title = stringResource(R.string.group_leave),
                subtitle = stringResource(R.string.group_leave_hint),
                icon = BleskIcons.Back,
                showChevron = false,
                onClick = { run { actions.leave() } },
            )
        }
    }
}

@Composable
private fun MemberRow(
    name: String,
    role: GroupRole,
    canManage: Boolean,
    canPromote: Boolean,
    onSetRole: (GroupRole) -> Unit,
    onRemove: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Avatar(name, size = 40.dp)
            Text(name, style = Blesk.type.bodyStrong, maxLines = 1, modifier = Modifier.weight(1f))
            when (role) {
                GroupRole.Owner -> Tag(stringResource(R.string.group_role_owner))
                GroupRole.Admin -> Tag(stringResource(R.string.group_role_admin))
                GroupRole.Member -> Unit
            }
            if (canManage) IconButton(BleskIcons.Settings, name, { open = !open })
        }
        if (open && canManage) {
            Row(
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canPromote) {
                    val promote = role == GroupRole.Member
                    Button(
                        text = stringResource(if (promote) R.string.group_make_admin else R.string.group_make_member),
                        kind = ButtonKind.Secondary,
                        onClick = { onSetRole(if (promote) GroupRole.Admin else GroupRole.Member) },
                    )
                }
                Button(text = stringResource(R.string.group_remove), kind = ButtonKind.Secondary, onClick = onRemove)
            }
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
    SectionHeader(stringResource(R.string.group_channels))
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
