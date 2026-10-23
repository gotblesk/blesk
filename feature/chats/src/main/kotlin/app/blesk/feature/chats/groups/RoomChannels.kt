package app.blesk.feature.chats.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.SlideToConfirm
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.launch

/** What is open over a room's channels: the menu of one, one of its sheets, or «+ Канал». */
sealed interface ChannelTarget {
    data object Add : ChannelTarget

    data class Menu(val channelId: String) : ChannelTarget

    data class Edit(val channelId: String) : ChannelTarget

    data class Rights(val channelId: String) : ChannelTarget

    data class Delete(val channelId: String) : ChannelTarget
}

/**
 * The room's channels in order. Whoever may manage channels taps or long-presses one for its menu
 * and adds new ones with «+ Канал»; what opens goes to [onTarget], for [ChannelSheets] to show over
 * the whole screen rather than inside its scrolling content.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsSection(env: ManageEnv, onTarget: (ChannelTarget) -> Unit) {
    val manage = env.can(Right.Channels)
    val haptics = rememberHaptics()
    val visible = env.state.channels.filter { GroupPolicy.sees(env.state, env.me, it) }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        visible.forEach { channel ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = Blesk.dimens.touchTarget)
                    .combinedClickable(
                        enabled = manage,
                        role = Role.Button,
                        onLongClick = {
                            haptics.menu()
                            onTarget(ChannelTarget.Menu(channel.id))
                        },
                        onClick = { onTarget(ChannelTarget.Menu(channel.id)) },
                    )
                    .padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    icon = if (channel.kind == GroupChannelKind.Voice) BleskIcons.Voice else BleskIcons.Hash,
                    contentDescription = null,
                    tint = Blesk.colors.textSecondary,
                    size = 20.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(channel.title, style = Blesk.type.bodyStrong, maxLines = 1)
                    if (channel.description.isNotBlank()) {
                        Text(channel.description, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
                    }
                }
                if (channel.adminsOnly) Tag(stringResource(R.string.channel_kind_announce))
                if (GroupPolicy.EVERYONE in channel.hiddenFrom) {
                    Icon(
                        BleskIcons.Lock,
                        contentDescription = stringResource(R.string.channel_closed),
                        tint = Blesk.colors.textTertiary,
                        size = 16.dp,
                    )
                }
            }
        }
    }
    if (manage && env.state.channels.size < GroupPolicy.MAX_CHANNELS) {
        Button(
            text = stringResource(R.string.channel_add_capsule),
            kind = ButtonKind.Secondary,
            onClick = { onTarget(ChannelTarget.Add) },
            modifier = Modifier.padding(Blesk.dimens.gutter),
        )
    }
}

/**
 * [ChannelSheets] for the room's cluster in the list, which has no [ManageEnv] of its own; the
 * room's details show the same sheets with theirs. Shown only to those who may manage channels.
 */
@Composable
fun RoomChannelSheets(
    state: GroupState,
    me: String,
    actions: GroupActions,
    target: ChannelTarget?,
    onTarget: (ChannelTarget?) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val refused = stringResource(R.string.manage_refused)
    val env = ManageEnv(
        state = state,
        me = me,
        nameOf = { it },
        change = actions.change,
        run = { block -> scope.launch { block() } },
        inviteLink = { _, code -> code },
        gate = null,
        onOpenPerson = {},
        onGone = {},
        refused = { notify(refused) },
    )
    ChannelSheets(env, actions, target, onTarget)
}

/**
 * The menu of a channel and its «Изменить», «Права» and «Удалить» sheets, or «+ Канал»: whichever
 * [target] names. Drawn over the screen, so it is placed beside the screen's content, not in it.
 * Once the channel is gone, or the right to manage channels, the sheet closes for good.
 */
@Composable
internal fun ChannelSheets(env: ManageEnv, actions: GroupActions, target: ChannelTarget?, onTarget: (ChannelTarget?) -> Unit) {
    if (target == null) return
    val close = { onTarget(null) }
    if (!env.can(Right.Channels)) return SubjectGone(close)
    fun channel(id: String) = env.state.channels.firstOrNull { it.id == id }
    when (target) {
        ChannelTarget.Add -> NewChannelSheet(env, actions, onClose = close)
        is ChannelTarget.Menu -> {
            val channel = channel(target.channelId) ?: return SubjectGone(close)
            ChannelMenu(env, channel, onTarget)
        }
        is ChannelTarget.Edit -> {
            val channel = channel(target.channelId) ?: return SubjectGone(close)
            MenuSheet(title = stringResource(R.string.channel_menu_edit), onDismiss = close) {
                val title = rememberTextFieldState(channel.title)
                val description = rememberTextFieldState(channel.description)
                // Large text over a keyboard on a small phone: the fields scroll, the button stays whole below them.
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    TextInput(label = stringResource(R.string.group_channel_name), state = title)
                    TextInput(label = stringResource(R.string.channel_description), state = description, singleLine = false)
                }
                Button(
                    text = stringResource(R.string.group_save),
                    onClick = {
                        env.submit(GroupChange.RenameChannel(channel.id, title.text.toString(), description.text.toString()))
                        close()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        }
        is ChannelTarget.Rights -> {
            val channel = channel(target.channelId) ?: return SubjectGone(close)
            MenuSheet(title = channel.title, onDismiss = close) { ChannelRights(env, channel) }
        }
        is ChannelTarget.Delete -> {
            val channel = channel(target.channelId) ?: return SubjectGone(close)
            MenuSheet(title = channel.title, onDismiss = close) {
                Text(
                    stringResource(R.string.channel_delete_hint),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
                SlideToConfirm(
                    label = stringResource(R.string.channel_delete_slide),
                    actionLabel = stringResource(R.string.channel_delete_slide),
                    onConfirm = {
                        env.run { if (!actions.removeChannel(channel.id)) env.refused() }
                        close()
                    },
                )
            }
        }
    }
}

/**
 * What may be done to [channel]. It moves past the channels its mover sees: one hidden from them in between would leave
 * it, to their eyes, where it was. The room's last text channel is not deleted: there would be nowhere left to write.
 */
@Composable
private fun ChannelMenu(env: ManageEnv, channel: GroupChannel, onTarget: (ChannelTarget?) -> Unit) {
    val all = env.state.channels
    val visible = all.filter { GroupPolicy.sees(env.state, env.me, it) }
    val at = visible.indexOf(channel)
    val lastText = channel.kind == GroupChannelKind.Text && all.none { it.id != channel.id && it.kind == GroupChannelKind.Text }
    ChoiceSheet(
        title = channel.title,
        choices = buildList {
            add(Choice(stringResource(R.string.channel_menu_edit)) { onTarget(ChannelTarget.Edit(channel.id)) })
            add(Choice(stringResource(R.string.channel_menu_rights)) { onTarget(ChannelTarget.Rights(channel.id)) })
            fun moveTo(neighbour: GroupChannel) = env.submit(GroupChange.MoveChannel(channel.id, all.indexOf(neighbour)))
            visible.getOrNull(at - 1)?.let { above -> add(Choice(stringResource(R.string.channel_menu_up)) { moveTo(above) }) }
            visible.getOrNull(at + 1)?.let { below -> add(Choice(stringResource(R.string.channel_menu_down)) { moveTo(below) }) }
            if (!lastText) {
                add(Choice(stringResource(R.string.channel_menu_delete), danger = true) { onTarget(ChannelTarget.Delete(channel.id)) })
            }
        },
        onDismiss = { onTarget(null) },
    )
}

/**
 * Who sees the channel, as switches: «Все участники» first, then every role. While everyone sees
 * the channel a role cannot take it away from its holders, so the roles show as on and stay put.
 * Closing the channel to everyone leaves it open to every role, which are then shut out one by one.
 */
@Composable
private fun ColumnScope.ChannelRights(env: ManageEnv, channel: GroupChannel) {
    val live = env.state.channels.firstOrNull { it.id == channel.id } ?: channel
    val open = GroupPolicy.EVERYONE !in live.hiddenFrom
    fun hide(hidden: Set<String>) = env.submit(GroupChange.HideChannel(channel.id, hidden))
    Text(
        stringResource(R.string.channel_rights_hint),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
    Card(Modifier.weight(1f, fill = false)) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            ToggleRow(
                title = stringResource(R.string.manage_role_everyone),
                checked = open,
                onCheckedChange = { shown -> hide(if (shown) emptySet() else setOf(GroupPolicy.EVERYONE)) },
            )
            val untitled = stringResource(R.string.manage_role_untitled)
            env.state.roles.forEach { role ->
                ToggleRow(
                    title = role.title.ifBlank { untitled },
                    checked = open || role.id !in live.hiddenFrom,
                    enabled = !open,
                    onCheckedChange = { shown -> hide(if (shown) live.hiddenFrom - role.id else live.hiddenFrom + role.id) },
                )
            }
        }
    }
    if (open && env.state.roles.isNotEmpty()) {
        Text(
            stringResource(R.string.channel_rights_roles_hint),
            style = Blesk.type.caption,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NewChannelSheet(env: ManageEnv, actions: GroupActions, onClose: () -> Unit) {
    val title = rememberTextFieldState()
    var kind by remember { mutableIntStateOf(0) }
    MenuSheet(title = stringResource(R.string.group_channel_add), onDismiss = onClose) {
        Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            TextInput(label = stringResource(R.string.group_channel_name), state = title)
            Segmented(
                options = persistentListOf(
                    stringResource(R.string.group_channel_text),
                    stringResource(R.string.group_channel_voice),
                    stringResource(R.string.channel_kind_announce),
                ),
                selected = kind,
                onSelect = { kind = it },
            )
        }
        // Appears with a name: closing the sheet on an empty one would look like a channel was made.
        if (title.text.isNotBlank()) {
            Button(
                text = stringResource(R.string.group_create),
                icon = BleskIcons.Plus,
                onClick = {
                    val name = title.text.toString().trim()
                    val channelKind = if (kind == 1) GroupChannelKind.Voice else GroupChannelKind.Text
                    env.run { if (!actions.addChannel(name, channelKind, kind == 2)) env.refused() }
                    onClose()
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            )
        }
    }
}
