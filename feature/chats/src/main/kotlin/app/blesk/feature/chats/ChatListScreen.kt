package app.blesk.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.ScreenHeader
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun ChatListScreen(
    conversations: ImmutableList<Conversation>,
    demo: Boolean,
    now: Instant,
    onOpenConversation: (String) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(Blesk.colors.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(Blesk.dimens.rowGap),
        ) {
            item(key = "header") {
                ScreenHeader(
                    title = stringResource(R.string.chats_title),
                    actions = {
                        if (demo) DemoTag(Modifier.padding(end = 4.dp))
                        IconButton(BleskIcons.Settings, stringResource(R.string.chats_settings), onOpenSettings)
                    },
                )
            }
            items(conversations, key = { it.id }) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    now = now,
                    onClick = { onOpenConversation(conversation.id) },
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter - 6.dp),
                )
            }
            item(key = "bottom") { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun ConversationRow(conversation: Conversation, now: Instant, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val protected = conversation.protection == Protection.EndToEnd
    val encryptedLabel = stringResource(R.string.chats_encrypted)
    Capsule(
        modifier = modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {
                if (protected) contentDescription = "${conversation.title}, $encryptedLabel"
            },
        protected = protected,
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Avatar(conversation.title)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = conversation.title,
                                style = Blesk.type.bodyStrong,
                                maxLines = 1,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            KindTag(conversation)
                        }
                        Text(formatTime(conversation.updatedAt, now), style = Blesk.type.caption, color = Blesk.colors.textTertiary)
                    }
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = previewText(conversation),
                            style = Blesk.type.label,
                            color = Blesk.colors.textSecondary,
                            maxLines = 1,
                            modifier = Modifier.weight(1f),
                        )
                        if (conversation.unread > 0) UnreadBadge(conversation.unread)
                    }
                }
            }
            if (conversation.channels.isNotEmpty()) {
                RoomChannels(conversation.channels, Modifier.padding(start = Blesk.dimens.avatar + 14.dp, top = 10.dp))
            }
        }
    }
}

@Composable
private fun KindTag(conversation: Conversation) {
    when {
        conversation.kind == ConversationKind.Bot -> Tag(stringResource(R.string.chats_bot))
        conversation.kind == ConversationKind.Channel -> Tag(stringResource(R.string.chats_channel))
        conversation.protection == Protection.Open -> NotEncryptedTag()
    }
}

@Composable
private fun previewText(conversation: Conversation): String {
    val preview = conversation.preview ?: return when (conversation.kind) {
        ConversationKind.Channel -> pluralStringResource(R.plurals.chats_subscribers, conversation.memberCount, conversation.memberCount)
        else -> pluralStringResource(R.plurals.chats_members, conversation.memberCount, conversation.memberCount)
    }
    return if (preview.authorName != null) "${preview.authorName}: ${preview.text}" else preview.text
}

@Composable
private fun UnreadBadge(count: Int) {
    val label = pluralStringResource(R.plurals.chats_unread, count, count)
    Box(
        modifier = Modifier
            .height(20.dp)
            .background(Blesk.colors.accent, CircleShape)
            .padding(horizontal = 7.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = Blesk.type.caption, color = Blesk.colors.onAccent)
    }
}

@Composable
private fun RoomChannels(channels: ImmutableList<RoomChannel>, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        channels.forEach { channel ->
            val description = when (channel.kind) {
                RoomChannelKind.Text -> stringResource(R.string.chats_text_channel, channel.title)
                RoomChannelKind.Voice -> stringResource(R.string.chats_voice_channel, channel.title)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = description },
            ) {
                Icon(
                    icon = if (channel.kind == RoomChannelKind.Voice) BleskIcons.Voice else BleskIcons.Hash,
                    contentDescription = null,
                    tint = Blesk.colors.textTertiary,
                    size = 16.dp,
                )
                Text(channel.title, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                if (channel.kind == RoomChannelKind.Voice && channel.participants.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        channel.participants.forEach { person ->
                            val speaking = person.id in channel.speaking
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(if (speaking) Blesk.colors.light else Blesk.colors.textTertiary, CircleShape),
                            )
                        }
                    }
                }
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM")

private fun formatTime(at: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val day = at.atZone(zone).truncatedTo(ChronoUnit.DAYS)
    val today = now.atZone(zone).truncatedTo(ChronoUnit.DAYS)
    return if (day == today) timeFormat.format(at.atZone(zone)) else dateFormat.format(at.atZone(zone))
}
