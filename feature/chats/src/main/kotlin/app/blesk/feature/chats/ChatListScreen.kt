package app.blesk.feature.chats

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.haptics.rememberHaptics
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * The list of conversations. Rows lie on the background; pinned chats sit on capsules that
 * stick together into one cluster, and rooms appear as clusters of their channels.
 */
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
    /** Long press on a chat; null where nothing is offered. */
    onLongPress: ((Conversation) -> Unit)? = null,
    header: (LazyListScope.() -> Unit)? = null,
) {
    val pinned = conversations.filter { it.pinned && !it.isRoomCluster() }
    val rest = conversations.filterNot { it.pinned && !it.isRoomCluster() }
    val rowModifier = Modifier.padding(horizontal = Blesk.dimens.gutter - 6.dp)
    LazyColumn(
        state = state,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentPadding.calculateTopPadding() + 4.dp,
            bottom = contentPadding.calculateBottomPadding() + 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        header?.invoke(this)
        if (pinned.isNotEmpty()) {
            item(key = "pinned") {
                PinnedCluster(pinned.toImmutableList(), rowModifier.padding(bottom = 8.dp)) { conversation ->
                    ConversationRow(
                        conversation = conversation,
                        typing = conversation.id in typing,
                        now = now,
                        onClick = { onOpenConversation(conversation.id) },
                        onLongClick = onLongPress?.let { press -> { press(conversation) } },
                        onCapsule = true,
                    )
                }
            }
        }
        items(rest, key = { it.id }) { conversation ->
            if (conversation.isRoomCluster()) {
                RoomCluster(
                    room = conversation,
                    initiallyOpen = conversation.id in openRooms,
                    now = now,
                    onOpenChannel = { onOpenChannel(conversation, it) },
                    modifier = rowModifier.padding(vertical = 6.dp),
                )
            } else {
                ConversationRow(
                    conversation = conversation,
                    typing = conversation.id in typing,
                    now = now,
                    onClick = { onOpenConversation(conversation.id) },
                    onLongClick = onLongPress?.let { press -> { press(conversation) } },
                    modifier = rowModifier,
                )
            }
        }
    }
}

private fun Conversation.isRoomCluster() = kind == ConversationKind.Room && channels.isNotEmpty()

/** Pinned chats: each on a capsule, all of them stuck together into one drop. */
@Composable
private fun PinnedCluster(
    pinned: ImmutableList<Conversation>,
    modifier: Modifier = Modifier,
    row: @Composable (Conversation) -> Unit,
) {
    val rects = remember { PinnedGeometry() }
    val radius = with(LocalDensity.current) { Blesk.dimens.capsuleRadius.toPx() }
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .metaballs(color = Blesk.colors.surface, smoothness = 16.dp) { rects.blobs(radius) },
        content = { pinned.forEach { key(it.id) { row(it) } } },
    ) { measurables, constraints ->
        val loose = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(loose) }
        val gap = PinnedGap.roundToPx()
        val height = placeables.sumOf { it.height } + gap * (placeables.size - 1).coerceAtLeast(0)
        var y = 0
        rects.rows = placeables.map { placeable ->
            Rect(0f, y.toFloat(), constraints.maxWidth.toFloat(), (y + placeable.height).toFloat()).also { y += placeable.height + gap }
        }
        layout(constraints.maxWidth, height) {
            var top = 0
            placeables.forEach {
                it.place(0, top)
                top += it.height + gap
            }
        }
    }
}

/** Where the pinned capsules are, written during layout and read while drawing. */
private class PinnedGeometry {
    var rows: List<Rect> = emptyList()

    fun blobs(radius: Float) = rows.map { Blob(it, radius) }
}

private val PinnedGap = 4.dp

/** A chat row on the background; a capsule rises under the finger while it is pressed. */
@Composable
private fun ConversationRow(
    conversation: Conversation,
    typing: Boolean,
    now: Instant,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    onCapsule: Boolean = false,
) {
    val protected = conversation.protection == Protection.EndToEnd
    val haptics = rememberHaptics()
    val encryptedLabel = stringResource(R.string.chats_encrypted)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && !onCapsule) 1f else 0f, label = "press")
    val capsule = Blesk.colors.surface
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .drawBehind { if (press > 0f) drawRect(capsule.copy(alpha = capsule.alpha * press)) }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.chats_menu),
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.merge()
                        action()
                    }
                },
                onClick = onClick,
            )
            .semantics(mergeDescendants = true) {
                if (protected) contentDescription = "${conversation.title}, $encryptedLabel"
            },
    ) {
        ConversationSummary(conversation, typing, now, Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

@Composable
internal fun ConversationSummary(conversation: Conversation, typing: Boolean, now: Instant, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Avatar(
            name = conversation.title,
            key = conversation.id,
            protected = conversation.protection == Protection.EndToEnd &&
                conversation.kind != ConversationKind.Channel && conversation.kind != ConversationKind.Bot,
        )
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
                if (conversation.pinned) {
                    Icon(
                        BleskIcons.Pin,
                        contentDescription = stringResource(R.string.chats_pinned),
                        size = 14.dp,
                        tint = Blesk.colors.textTertiary,
                    )
                }
                Text(formatTime(conversation.updatedAt, now), style = Blesk.type.caption, color = Blesk.colors.textTertiary)
            }
            Row(
                modifier = Modifier.padding(top = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val draft = conversation.draft.isNotBlank() && !typing
                Text(
                    text = when {
                        typing -> stringResource(R.string.chats_typing)
                        draft -> stringResource(R.string.chats_draft, conversation.draft.lines().first())
                        else -> previewText(conversation)
                    },
                    style = Blesk.type.label,
                    color = if (typing || draft) Blesk.colors.accent else Blesk.colors.textSecondary,
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
