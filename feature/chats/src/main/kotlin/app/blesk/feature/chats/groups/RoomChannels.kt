package app.blesk.feature.chats.groups

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.SlideToConfirm
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.MenuSheet
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.Right
import kotlinx.collections.immutable.persistentListOf

private enum class ChannelSheet { Menu, Edit, Rights, Delete }

/**
 * The room's channels in order. Whoever may manage channels taps or long-presses one for
 * «Изменить», «Права», «Выше», «Ниже» and «Удалить» (with a slider), and adds new ones with «+ Канал».
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ChannelsSection(env: ManageEnv, actions: GroupActions) {
    val manage = env.can(Right.Channels)
    val haptics = rememberHaptics()
    var open by remember { mutableStateOf<Pair<GroupChannel, ChannelSheet>?>(null) }
    var adding by remember { mutableStateOf(false) }
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
                            open = channel to ChannelSheet.Menu
                        },
                        onClick = { open = channel to ChannelSheet.Menu },
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
                if (channel.hiddenFrom.isNotEmpty()) {
                    Icon(BleskIcons.Lock, contentDescription = null, tint = Blesk.colors.textTertiary, size = 16.dp)
                }
            }
        }
    }
    if (manage) {
        Button(
            text = stringResource(R.string.channel_add_capsule),
            kind = ButtonKind.Secondary,
            onClick = { adding = true },
            modifier = Modifier.padding(Blesk.dimens.gutter),
        )
    }
    if (adding) NewChannelSheet(env, actions, onClose = { adding = false })
    open?.let { (channel, sheet) ->
        val close = { open = null }
        when (sheet) {
            ChannelSheet.Menu -> {
                val index = env.state.channels.indexOfFirst { it.id == channel.id }
                ChoiceSheet(
                    title = channel.title,
                    choices = buildList {
                        add(Choice(stringResource(R.string.channel_menu_edit)) { open = channel to ChannelSheet.Edit })
                        add(Choice(stringResource(R.string.channel_menu_rights)) { open = channel to ChannelSheet.Rights })
                        fun move(to: Int) = env.run { env.change(GroupChange.MoveChannel(channel.id, to)) }
                        if (index > 0) add(Choice(stringResource(R.string.channel_menu_up)) { move(index - 1) })
                        if (index < env.state.channels.lastIndex) {
                            add(Choice(stringResource(R.string.channel_menu_down)) { move(index + 1) })
                        }
                        add(Choice(stringResource(R.string.channel_menu_delete), danger = true) { open = channel to ChannelSheet.Delete })
                    },
                    onDismiss = close,
                )
            }
            ChannelSheet.Edit -> MenuSheet(title = stringResource(R.string.channel_menu_edit), onDismiss = close) {
                val title = rememberTextFieldState(channel.title)
                val description = rememberTextFieldState(channel.description)
                TextInput(label = stringResource(R.string.group_channel_name), state = title)
                TextInput(label = stringResource(R.string.channel_description), state = description, singleLine = false)
                Button(
                    text = stringResource(R.string.group_save),
                    onClick = {
                        env.run { env.change(GroupChange.RenameChannel(channel.id, title.text.toString(), description.text.toString())) }
                        close()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
            ChannelSheet.Rights -> MenuSheet(title = channel.title, onDismiss = close) {
                ChannelRights(env, channel)
            }
            ChannelSheet.Delete -> MenuSheet(title = channel.title, onDismiss = close) {
                Text(
                    stringResource(R.string.channel_delete_hint),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                )
                SlideToConfirm(
                    label = stringResource(R.string.channel_delete_slide),
                    actionLabel = stringResource(R.string.channel_delete_slide),
                    onConfirm = {
                        env.run { actions.removeChannel(channel.id) }
                        close()
                    },
                )
            }
        }
    }
}

/** Who sees the channel: every role, and everyone without one, as switches. */
@Composable
private fun ChannelRights(env: ManageEnv, channel: GroupChannel) {
    val live = env.state.channels.firstOrNull { it.id == channel.id } ?: channel
    Text(
        stringResource(R.string.channel_rights_hint),
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
    )
    Card {
        Column(Modifier.heightIn(max = SheetMax).verticalScroll(rememberScrollState())) {
            val groups = env.state.roles.map { it.id to it.title } + (GroupPolicy.EVERYONE to stringResource(R.string.manage_role_everyone))
            groups.forEach { (id, title) ->
                ToggleRow(title = title, checked = id !in live.hiddenFrom, onCheckedChange = { shown ->
                    val hidden = if (shown) live.hiddenFrom - id else live.hiddenFrom + id
                    env.run { env.change(GroupChange.HideChannel(channel.id, hidden)) }
                })
            }
        }
    }
}

@Composable
private fun NewChannelSheet(env: ManageEnv, actions: GroupActions, onClose: () -> Unit) {
    val title = rememberTextFieldState()
    var kind by remember { mutableIntStateOf(0) }
    MenuSheet(title = stringResource(R.string.group_channel_add), onDismiss = onClose) {
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
        Button(
            text = stringResource(R.string.group_create),
            icon = BleskIcons.Plus,
            onClick = {
                val name = title.text.toString().trim()
                if (name.isNotEmpty()) {
                    val channelKind = if (kind == 1) GroupChannelKind.Voice else GroupChannelKind.Text
                    env.run { actions.addChannel(name, channelKind, kind == 2) }
                }
                onClose()
            },
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
        )
    }
}
