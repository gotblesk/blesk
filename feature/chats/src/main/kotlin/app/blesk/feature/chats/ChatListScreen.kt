package app.blesk.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Markup
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
    /** Swipe left: into the archive (or out of it); null turns the swipe off. */
    onSwipeArchive: ((Conversation) -> Unit)? = null,
    /** Swipe right: read, or back to unread. */
    onSwipeRead: ((Conversation) -> Unit)? = null,
    archivedCount: Int = 0,
    onOpenArchive: (() -> Unit)? = null,
) {
    val pinned = conversations.filter { it.pinned && !it.isRoomCluster() }.sortedByDescending { it.kind == ConversationKind.Saved }
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
        if (archivedCount > 0 && onOpenArchive != null) {
            item(key = "archive") { ArchiveRow(archivedCount, onOpenArchive, rowModifier) }
        }
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
                SwipeRow(
                    conversation = conversation,
                    onArchive = onSwipeArchive?.let { swipe -> { swipe(conversation) } },
                    onRead = onSwipeRead?.let { swipe -> { swipe(conversation) } },
                    modifier = rowModifier,
                ) {
                    ConversationRow(
                        conversation = conversation,
                        typing = conversation.id in typing,
                        now = now,
                        onClick = { onOpenConversation(conversation.id) },
                        onLongClick = onLongPress?.let { press -> { press(conversation) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveRow(count: Int, onOpen: () -> Unit, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .clickable(role = Role.Button, onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(Blesk.dimens.avatar).background(Blesk.colors.surface, CircleShape), contentAlignment = Alignment.Center) {
            Icon(BleskIcons.Archive, contentDescription = null, tint = Blesk.colors.textSecondary)
        }
        Text(stringResource(R.string.chats_archive), style = Blesk.type.bodyStrong, modifier = Modifier.weight(1f))
        Text(pluralStringResource(R.plurals.chats_archived, count, count), style = Blesk.type.label, color = Blesk.colors.textSecondary)
    }
}

/**
 * A row that follows the finger sideways: far enough to the left archives it, to the right
 * toggles read. TalkBack gets the same as named actions.
 */
@Composable
private fun SwipeRow(
    conversation: Conversation,
    onArchive: (() -> Unit)?,
    onRead: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (onArchive == null && onRead == null) {
        Box(modifier) { content() }
        return
    }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val threshold = with(LocalDensity.current) { SwipeThreshold.toPx() }
    val unread = conversation.unread > 0 || conversation.markedUnread
    val archiveLabel = stringResource(
        if (conversation.archived) R.string.chats_from_archive else R.string.chats_to_archive,
    )
    val readLabel = stringResource(if (unread) R.string.chats_mark_read else R.string.chats_mark_unread)
    Box(
        modifier
            .fillMaxWidth()
            .semantics {
                customActions = listOfNotNull(
                    onArchive?.let { CustomAccessibilityAction(archiveLabel) { it(); true } },
                    onRead?.let { CustomAccessibilityAction(readLabel) { it(); true } },
                )
            },
    ) {
        val progress = (abs(offset.value) / threshold).coerceIn(0f, 1f)
        if (offset.value != 0f) {
            Row(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                    .background(Blesk.colors.surface.copy(alpha = progress)),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (offset.value > 0) Arrangement.Start else Arrangement.End,
            ) {
                Icon(
                    icon = if (offset.value > 0) BleskIcons.Check else BleskIcons.Archive,
                    contentDescription = null,
                    tint = Blesk.colors.textSecondary.copy(alpha = progress),
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
        }
        Box(
            Modifier
                .graphicsLayer { translationX = offset.value }
                .pointerInput(onArchive, onRead) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val value = offset.value
                            when {
                                value <= -threshold && onArchive != null -> onArchive()
                                value >= threshold && onRead != null -> onRead()
                            }
                            if (abs(value) >= threshold) haptics.detach()
                            scope.launch { offset.animateTo(0f) }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, delta ->
                        val next = offset.value + delta
                        val allowed = (next < 0 && onArchive != null) || (next > 0 && onRead != null)
                        if (allowed || next == 0f) {
                            change.consume()
                            scope.launch { offset.snapTo(next.coerceIn(-threshold * 1.4f, threshold * 1.4f)) }
                        }
                    }
                },
        ) { content() }
    }
}

private val SwipeThreshold = 96.dp

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
    val muted = conversation.muted(now.toEpochMilli())
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        if (conversation.kind == ConversationKind.Saved) {
            SavedAvatar()
        } else {
            Avatar(
                name = conversation.title,
                key = conversation.id,
                protected = conversation.protection == Protection.EndToEnd &&
                    conversation.kind != ConversationKind.Channel && conversation.kind != ConversationKind.Bot,
            )
        }
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
                    if (muted) {
                        Icon(
                            BleskIcons.BellOff,
                            contentDescription = stringResource(R.string.chats_muted),
                            size = 14.dp,
                            tint = Blesk.colors.textTertiary,
                        )
                    }
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
                when {
                    conversation.unread > 0 -> UnreadBadge(conversation.unread, muted)
                    conversation.markedUnread -> UnreadDot(muted)
                }
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
    val text = Markup.parse(preview.text).masked(stringResource(R.string.chat_spoiler))
    return if (preview.authorName != null) "${preview.authorName}: $text" else text
}

@Composable
internal fun UnreadBadge(count: Int, muted: Boolean = false) {
    val label = pluralStringResource(R.plurals.chats_unread, count, count)
    Box(
        modifier = Modifier
            .height(20.dp)
            .background(if (muted) Blesk.colors.surfaceStrong else Blesk.colors.accent, CircleShape)
            .padding(horizontal = 7.dp)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(count.toString(), style = Blesk.type.caption, color = if (muted) Blesk.colors.textSecondary else Blesk.colors.onAccent)
    }
}

/** A chat you marked to come back to. */
@Composable
private fun UnreadDot(muted: Boolean) {
    val label = stringResource(R.string.chats_marked_unread)
    Box(
        Modifier
            .size(12.dp)
            .background(if (muted) Blesk.colors.textTertiary else Blesk.colors.accent, CircleShape)
            .semantics { contentDescription = label },
    )
}

/** «Избранное» has no person behind it, so it shows a bookmark instead of a letter. */
@Composable
private fun SavedAvatar() {
    Box(
        Modifier.size(Blesk.dimens.avatar).background(Blesk.colors.surfaceStrong, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BleskIcons.Bookmark, contentDescription = null, tint = Blesk.colors.textPrimary)
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
