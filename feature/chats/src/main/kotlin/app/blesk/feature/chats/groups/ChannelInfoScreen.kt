package app.blesk.feature.chats.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.Archive
import app.blesk.feature.chats.ArchiveActions
import app.blesk.feature.chats.ArchiveKind
import app.blesk.feature.chats.R
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** How a channel tells about new messages: always, only when you are named with @, or never. */
enum class ChannelNotify { All, Mentions, Nothing }

/** Someone who sees the channel: everyone in its room. */
class ChannelMember(val username: String, val name: String)

class ChannelInfoActions(
    val onNotify: (ChannelNotify) -> Unit,
    val onOpenRoom: () -> Unit,
    val onOpenMessage: (messageId: String) -> Unit,
    val onOpenPerson: (username: String) -> Unit,
    val onTheme: () -> Unit,
    val archive: ArchiveActions,
)

private enum class ChannelTab { Media, Files, Links, Voice, Pinned, Members }

/**
 * A room's channel, opened from its header like a person's profile from theirs: its name and the
 * room it belongs to, how it notifies, and what was sent there — «Медиа · Файлы · Ссылки ·
 * Голосовые», the pinned messages and who sees it. The room row leads on to the room itself;
 * «Оформление чата» sits under «Ещё».
 */
@Composable
fun ChannelInfoScreen(
    title: String,
    room: String,
    notify: ChannelNotify,
    messages: ImmutableList<Message>,
    pinned: ImmutableList<Message>,
    members: ImmutableList<ChannelMember>,
    actions: ChannelInfoActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var more by remember { mutableStateOf(false) }
    val gutter = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(
        title = "# $title",
        onBack = onBack,
        modifier = modifier,
        actions = { IconButton(BleskIcons.More, stringResource(R.string.person_more), { more = true }) },
    ) {
        InfoHero(
            title = "# $title",
            lines = listOf(stringResource(R.string.channel_in_room, room)),
            avatar = {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(Blesk.colors.surfaceStrong),
                    contentAlignment = Alignment.Center,
                ) { Icon(BleskIcons.Hash, contentDescription = null, size = 32.dp) }
            },
        )
        Card(gutter) {
            ListRow(
                title = stringResource(R.string.channel_room_row, room),
                icon = BleskIcons.Rooms,
                onClick = actions.onOpenRoom,
            )
        }
        SectionHeader(stringResource(R.string.channel_notify))
        Segmented(
            options = persistentListOf(
                stringResource(R.string.channel_notify_all),
                stringResource(R.string.channel_notify_mentions),
                stringResource(R.string.channel_notify_nothing),
            ),
            selected = notify.ordinal,
            onSelect = { actions.onNotify(ChannelNotify.entries[it]) },
        )
        val labels = ChannelTab.entries.map { channelTab ->
            when (channelTab) {
                ChannelTab.Media -> stringResource(ArchiveKind.Media.label)
                ChannelTab.Files -> stringResource(ArchiveKind.Files.label)
                ChannelTab.Links -> stringResource(ArchiveKind.Links.label)
                ChannelTab.Voice -> stringResource(ArchiveKind.Voice.label)
                ChannelTab.Pinned -> stringResource(R.string.channel_tab_pinned)
                ChannelTab.Members -> stringResource(R.string.channel_tab_members)
            }
        }.toImmutableList()
        InfoTabs(options = labels, selected = tab, onSelect = { tab = it })
        when (val shown = ChannelTab.entries[tab]) {
            ChannelTab.Pinned -> PinnedList(pinned, actions.onOpenMessage)
            ChannelTab.Members -> Card(gutter) {
                members.forEach { member ->
                    ListRow(
                        title = member.name,
                        subtitle = "@${member.username}",
                        icon = BleskIcons.Person,
                        onClick = { actions.onOpenPerson(member.username) },
                    )
                }
            }
            else -> Archive(kind = ArchiveKind.entries[shown.ordinal], messages = messages, actions = actions.archive)
        }
    }
    if (more) {
        ChoiceSheet(
            title = "# $title",
            choices = listOf(Choice(stringResource(R.string.chat_theme)) { actions.onTheme() }),
            onDismiss = { more = false },
        )
    }
}

@Composable
private fun PinnedList(pinned: ImmutableList<Message>, onOpen: (String) -> Unit) {
    if (pinned.isEmpty()) {
        StateMessage(stringResource(R.string.channel_pinned_empty))
        return
    }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        pinned.forEach { message ->
            ListRow(
                title = message.text.ifBlank { stringResource(R.string.channel_pinned_attachment) },
                icon = BleskIcons.Pin,
                onClick = { onOpen(message.id) },
            )
        }
    }
}
