package app.blesk.feature.chats.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.SkeletonRows
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.EncryptionRow
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import app.blesk.core.design.R as DesignR

/** What can be done to a public space; each action refreshes what the screen shows. */
class SpaceActions(
    val update: suspend (title: String, about: String) -> Unit,
    val setRole: suspend (String, SpaceRole) -> Unit,
    val ban: suspend (String) -> Unit,
    val addChannel: suspend (String) -> Unit,
    val removeChannel: suspend (String) -> Unit,
    val leave: suspend () -> Unit,
    val delete: suspend () -> Unit,
)

@Composable
fun SpaceInfoScreen(
    space: SpaceInfo,
    me: String,
    /** Null while the list is on its way from the server. */
    members: ImmutableList<SpaceMember>?,
    nameOf: (String) -> String,
    actions: SpaceActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    membersFailed: Boolean = false,
    onRetryMembers: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val owner = space.role == SpaceRole.Owner
    val admin = owner || space.role == SpaceRole.Admin
    fun run(block: suspend () -> Unit) {
        scope.launch { block() }
    }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)

    ScrollScreen(title = space.title, onBack = onBack, modifier = modifier) {
        Column(pad.padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("@${space.handle}", style = Blesk.type.bodyStrong)
                NotEncryptedTag()
            }
            Text(
                text = pluralStringResource(R.plurals.space_members, space.members, space.members),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
            )
            Text(stringResource(R.string.space_open_note), style = Blesk.type.label, color = Blesk.colors.textSecondary)
        }
        EncryptionRow(protected = false)

        if (admin) {
            val title = rememberTextFieldState(space.title)
            val about = rememberTextFieldState(space.about)
            TextInput(label = stringResource(R.string.group_name), state = title)
            TextInput(label = stringResource(R.string.space_about), state = about, singleLine = false)
            Button(
                text = stringResource(R.string.group_save),
                kind = ButtonKind.Secondary,
                onClick = { run { actions.update(title.text.toString(), about.text.toString()) } },
                modifier = pad,
            )
        } else if (space.about.isNotBlank()) {
            Text(space.about, modifier = pad)
        }

        if (space.kind == SpaceKind.Room) {
            SpaceChannels(space, admin, actions, ::run)
        }

        if (admin && members == null) {
            SectionHeader(stringResource(R.string.group_members))
            if (membersFailed) {
                StateMessage(
                    text = stringResource(DesignR.string.design_failed),
                    action = stringResource(DesignR.string.design_retry),
                    onAction = onRetryMembers,
                )
            } else {
                SkeletonRows(count = 3, avatar = 40.dp)
            }
        }
        if (admin && !members.isNullOrEmpty()) {
            SectionHeader(stringResource(R.string.group_members))
            Card(pad) {
                members.forEach { member ->
                    SpaceMemberRow(
                        name = if (member.username == me) {
                            "${nameOf(me)} · ${stringResource(R.string.group_you)}"
                        } else {
                            nameOf(member.username)
                        },
                        role = member.role,
                        canManage = member.username != me && member.role != SpaceRole.Owner && (owner || member.role == SpaceRole.Member),
                        canPromote = owner,
                        onSetRole = { role -> run { actions.setRole(member.username, role) } },
                        onBan = { run { actions.ban(member.username) } },
                    )
                }
            }
        }

        Card(pad.padding(top = Blesk.dimens.sectionGap)) {
            if (owner) {
                ListRow(
                    title = stringResource(R.string.space_delete),
                    subtitle = stringResource(R.string.space_delete_hint),
                    icon = BleskIcons.Trash,
                    showChevron = false,
                    onClick = { run { actions.delete() } },
                )
            } else {
                ListRow(
                    title = stringResource(R.string.group_leave),
                    icon = BleskIcons.Back,
                    showChevron = false,
                    onClick = { run { actions.leave() } },
                )
            }
        }
    }
}

@Composable
private fun SpaceMemberRow(
    name: String,
    role: SpaceRole,
    canManage: Boolean,
    canPromote: Boolean,
    onSetRole: (SpaceRole) -> Unit,
    onBan: () -> Unit,
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
                SpaceRole.Owner -> Tag(stringResource(R.string.group_role_owner))
                SpaceRole.Admin -> Tag(stringResource(R.string.group_role_admin))
                SpaceRole.Member -> Unit
            }
            if (canManage) IconButton(BleskIcons.Settings, name, { open = !open })
        }
        if (open && canManage) {
            Row(
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (canPromote) {
                    val promote = role == SpaceRole.Member
                    Button(
                        text = stringResource(if (promote) R.string.group_make_admin else R.string.group_make_member),
                        kind = ButtonKind.Secondary,
                        onClick = { onSetRole(if (promote) SpaceRole.Admin else SpaceRole.Member) },
                    )
                }
                Button(text = stringResource(R.string.space_ban), kind = ButtonKind.Secondary, onClick = onBan)
            }
        }
    }
}

@Composable
private fun SpaceChannels(space: SpaceInfo, admin: Boolean, actions: SpaceActions, run: (suspend () -> Unit) -> Unit) {
    SectionHeader(stringResource(R.string.group_channels))
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        space.channels.forEach { channel ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(BleskIcons.Hash, contentDescription = null, tint = Blesk.colors.textSecondary, size = 20.dp)
                Text(channel.title, style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
                if (admin && space.channels.size > 1) {
                    IconButton(BleskIcons.Trash, stringResource(R.string.group_channel_delete, channel.title), {
                        run { actions.removeChannel(channel.id) }
                    })
                }
            }
        }
    }
    if (!admin) return
    val title = rememberTextFieldState()
    TextInput(label = stringResource(R.string.group_channel_name), state = title)
    Button(
        text = stringResource(R.string.space_add_channel),
        kind = ButtonKind.Secondary,
        onClick = {
            val name = title.text.toString().trim()
            if (name.isNotEmpty()) {
                title.clearText()
                run { actions.addChannel(name) }
            }
        },
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter),
    )
}
