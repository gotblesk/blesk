package app.blesk.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** The list of conversations; rooms appear as clusters of their channels. */
@Composable
fun ChatList(
    conversations: ImmutableList<Conversation>,
    typing: ImmutableSet<String>,
    now: Instant,
    contentPadding: PaddingValues,
    onOpenConversation: (String) -> Unit,
    onOpenChannel: (Conversation, RoomChannel) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    openRooms: ImmutableSet<String> = persistentSetOf(),
) {
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(Blesk.dimens.rowGap),
    ) {
        items(conversations, key = { it.id }) { conversation ->
            val rowModifier = Modifier.padding(horizontal = Blesk.dimens.gutter - 6.dp)
            if (conversation.kind == ConversationKind.Room && conversation.channels.isNotEmpty()) {
                RoomCluster(
                    room = conversation,
                    initiallyOpen = conversation.id in openRooms,
                    now = now,
                    onOpenChannel = { onOpenChannel(conversation, it) },
                    modifier = rowModifier,
                )
            } else {
                ConversationRow(
                    conversation = conversation,
                    typing = conversation.id in typing,
                    now = now,
                    onClick = { onOpenConversation(conversation.id) },
                    modifier = rowModifier,
                )
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: Conversation,
    typing: Boolean,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        ConversationSummary(conversation, typing, now, Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}

@Composable
internal fun ConversationSummary(conversation: Conversation, typing: Boolean, now: Instant, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                    text = if (typing) stringResource(R.string.chats_typing) else previewText(conversation),
                    style = Blesk.type.label,
                    color = if (typing) Blesk.colors.accent else Blesk.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unread > 0) UnreadBadge(conversation.unread)
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
        ConversationKind.Direct, ConversationKind.Bot -> ""
        else -> pluralStringResource(R.plurals.chats_members, conversation.memberCount, conversation.memberCount)
    }
    return if (preview.authorName != null) "${preview.authorName}: ${preview.text}" else preview.text
}

@Composable
internal fun UnreadBadge(count: Int) {
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

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormat = DateTimeFormatter.ofPattern("dd.MM")

internal fun formatTime(at: Instant, now: Instant): String {
    val zone = ZoneId.systemDefault()
    val day = at.atZone(zone).truncatedTo(ChronoUnit.DAYS)
    val today = now.atZone(zone).truncatedTo(ChronoUnit.DAYS)
    return if (day == today) timeFormat.format(at.atZone(zone)) else dateFormat.format(at.atZone(zone))
}
