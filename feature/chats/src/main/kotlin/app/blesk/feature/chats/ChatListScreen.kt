package app.blesk.feature.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Markup
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.GroupAvatar
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.Tag
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.motion.sharedElement
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.Haptics
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

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
    /** Strangers who wrote first; they gather in one «Запросы» row above the chats. */
    requests: ImmutableList<Conversation> = persistentListOf(),
    onOpenRequests: () -> Unit = {},
    /** Direct chats whose person is online right now; they get the dot of light. */
    online: ImmutableSet<String> = persistentSetOf(),
    onOpenArchive: (() -> Unit)? = null,
    /** Pinned chats dragged into a new order; null keeps the order fixed. */
    onReorderPinned: ((List<String>) -> Unit)? = null,
    /** Swipe left on a pinned chat unpins it. */
    onSwipeUnpin: ((Conversation) -> Unit)? = null,
    /** The archive row folded into the top lens: pulling the list down opens the archive. */
    archiveFolded: Boolean = false,
    onFoldArchive: ((Boolean) -> Unit)? = null,
    /** Where the rows are, for the long-press menu to lift the pressed one in place. */
    places: ChatRowPlaces? = null,
    /** Pinned rooms dragged into a new order; null keeps them where they are. */
    onReorderRooms: ((List<String>) -> Unit)? = null,
    /** The archive was opened by pulling the list down. */
    onPullArchive: () -> Unit = {},
    /** Rooms whose channels you may manage get «+ Канал» and a menu on each channel. */
    roomChannels: RoomChannelHandlers? = null,
) {
    val haptics = rememberHaptics()
    val threshold = with(LocalDensity.current) { PullThreshold.toPx() }
    val pull = remember { PullToArchive() }
    pull.threshold = threshold
    pull.onCross = haptics::tick
    pull.onOpen = {
        onPullArchive()
        onOpenArchive?.invoke()
    }
    val pulling = archiveFolded && archivedCount > 0 && onOpenArchive != null
    val pinned = conversations.filter { it.pinned && !it.isRoomCluster() }
        .sortedWith(compareBy<Conversation> { it.pinRank }.thenByDescending { it.kind == ConversationKind.Saved })
    val roomDrag = remember { RoomDrag() }
    val pinnedRooms = conversations.filter { it.pinned && it.isRoomCluster() }.map { it.id }
    if (roomDrag.lifted == null && roomDrag.order != pinnedRooms) roomDrag.order = pinnedRooms
    val rest = conversations.filterNot { it.pinned && !it.isRoomCluster() }.let { list ->
        // While a pinned room is held, the pinned rooms show in the order the finger makes.
        val byId = list.associateBy { it.id }
        roomDrag.order.mapNotNull(byId::get) + list.filterNot { it.id in roomDrag.order }
    }
    val rowModifier = Modifier.padding(horizontal = Blesk.dimens.gutter - 6.dp)
    CompositionLocalProvider(LocalOnline provides online, LocalChatRowPlaces provides places) {
        LazyColumn(
            state = state,
            modifier = modifier
                .fillMaxSize()
                .then(if (pulling) Modifier.nestedScroll(pull) else Modifier),
            contentPadding = PaddingValues(
                top = contentPadding.calculateTopPadding() + 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            header?.invoke(this)
            if (archivedCount > 0 && onOpenArchive != null) {
                if (archiveFolded) {
                    item(key = "archive-pull") { ArchivePull(pull, rowModifier) }
                } else {
                    item(key = "archive") {
                        SwipeRow(
                            conversation = null,
                            onArchive = onFoldArchive?.let { fold -> { fold(true) } },
                            onRead = null,
                            leftIcon = BleskIcons.ArrowUp,
                            leftLabel = stringResource(R.string.chats_archive_fold),
                            modifier = rowModifier.then(listMotion()),
                        ) { ArchiveRow(archivedCount, onOpenArchive, Modifier) }
                    }
                }
            }
            if (requests.isNotEmpty()) {
                item(key = "requests") { RequestsRow(requests, onOpenRequests, rowModifier.then(listMotion())) }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinned") {
                    PinnedCluster(
                        pinned = pinned.toImmutableList(),
                        onReorder = onReorderPinned,
                        onMenu = onLongPress,
                        modifier = rowModifier.then(listMotion()).padding(bottom = 8.dp),
                    ) { conversation, menuInGesture ->
                        SwipeRow(
                            conversation = conversation,
                            onArchive = onSwipeUnpin?.let { swipe -> { swipe(conversation) } },
                            onRead = null,
                            leftIcon = BleskIcons.Pin,
                            leftLabel = stringResource(R.string.chats_unpin),
                        ) {
                            ConversationRow(
                                conversation = conversation,
                                typing = conversation.id in typing,
                                now = now,
                                onClick = { onOpenConversation(conversation.id) },
                                // While the order can change, the long press lifts the capsule and the menu opens on release.
                                onLongClick = if (menuInGesture) null else onLongPress?.let { press -> { press(conversation) } },
                                onCapsule = true,
                            )
                        }
                    }
                }
            }
            items(rest, key = { it.id }) { conversation ->
                if (conversation.isRoomCluster()) {
                    val draggable = onReorderRooms != null && conversation.id in roomDrag.order
                    val menu = onLongPress?.let { press -> { press(conversation) } }
                    RoomCluster(
                        room = conversation,
                        initiallyOpen = conversation.id in openRooms,
                        now = now,
                        onOpenChannel = { onOpenChannel(conversation, it) },
                        onLongPress = menu.takeUnless { draggable },
                        onAddChannel = roomChannels?.takeIf { it.manages(conversation.id) }?.let { { it.onAdd(conversation.id) } },
                        onChannelMenu = roomChannels?.takeIf { it.manages(conversation.id) }?.let { handlers ->
                            { channel -> handlers.onMenu(conversation.id, channel) }
                        },
                        modifier = rowModifier
                            .then(if (roomDrag.lifted == conversation.id) Modifier.zIndex(1f) else listMotion())
                            .let { row ->
                                if (!draggable || onReorderRooms == null) return@let row
                                row.roomDrag(conversation.id, roomDrag, state, haptics, onMenu = menu, onDrop = onReorderRooms)
                            }
                            .padding(vertical = 6.dp),
                    )
                } else {
                    SwipeRow(
                        conversation = conversation,
                        onArchive = onSwipeArchive?.let { swipe -> { swipe(conversation) } },
                        onRead = onSwipeRead?.let { swipe -> { swipe(conversation) } },
                        modifier = rowModifier.then(listMotion()),
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
}

/** Who is online, for the dots on the avatars of the rows below. */
internal val LocalOnline = compositionLocalOf<Set<String>> { emptySet() }

/** Rows glide to their new place when a chat is pinned, archived or moves up with a new message. */
@Composable
private fun LazyItemScope.listMotion(): Modifier {
    val motion = Blesk.motion
    if (motion.reduced) return Modifier
    return Modifier.animateItem(fadeInSpec = motion.transition(), placementSpec = motion.settle(), fadeOutSpec = motion.transition())
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
 * A chat row that follows the finger sideways: far enough to the left archives it (or does
 * [leftLabel]), to the right toggles read.
 */
@Composable
private fun SwipeRow(
    conversation: Conversation?,
    onArchive: (() -> Unit)?,
    onRead: (() -> Unit)?,
    modifier: Modifier = Modifier,
    leftIcon: ImageVector = BleskIcons.Archive,
    leftLabel: String? = null,
    content: @Composable () -> Unit,
) {
    val unread = conversation != null && (conversation.unread > 0 || conversation.markedUnread)
    val archiveLabel = leftLabel ?: stringResource(
        if (conversation?.archived == true) R.string.chats_from_archive else R.string.chats_to_archive,
    )
    val readLabel = stringResource(if (unread) R.string.chats_mark_read else R.string.chats_mark_unread)
    SideSwipe(
        left = onArchive?.let { SwipeAction(leftIcon, archiveLabel, it) },
        right = onRead?.let { SwipeAction(BleskIcons.Check, readLabel, it) },
        modifier = modifier,
        content = content,
    )
}
private val PullThreshold = 88.dp

/** Resistance of the list when it is pulled past its top, so the pull feels heavier than a scroll. */
private const val PULL_RESISTANCE = 0.5f

/**
 * Pulling the list down past its top while the archive is folded: the pull is taken from the
 * overscroll, and letting go past [threshold] opens the archive.
 */
@Stable
private class PullToArchive : NestedScrollConnection {
    var pull by mutableFloatStateOf(0f)
    var threshold = 1f
    var onCross: () -> Unit = {}
    var onOpen: () -> Unit = {}

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (pull <= 0f || available.y >= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        val used = maxOf(available.y, -pull)
        pull += used
        return Offset(0f, used)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (available.y <= 0f || source != NestedScrollSource.UserInput) return Offset.Zero
        val before = pull
        pull += available.y * PULL_RESISTANCE
        if (before < threshold && pull >= threshold) onCross()
        return Offset(0f, available.y)
    }

    override suspend fun onPreFling(available: Velocity): Velocity {
        if (pull <= 0f) return Velocity.Zero
        val open = pull >= threshold
        pull = 0f
        if (open) onOpen()
        return available
    }
}

/** The folded archive showing through as the list is pulled down; it fills with light once letting go opens it. */
@Composable
private fun ArchivePull(pull: PullToArchive, modifier: Modifier) {
    val density = LocalDensity.current
    val progress = (pull.pull / pull.threshold).coerceIn(0f, 1f)
    val height = with(density) { pull.pull.toDp() }
    if (height <= 0.dp) return
    val ready = progress >= 1f
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .background(if (ready) Blesk.colors.light.copy(alpha = PULL_READY_ALPHA) else Blesk.colors.surface.copy(alpha = progress))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(BleskIcons.Archive, contentDescription = null, tint = Blesk.colors.textSecondary.copy(alpha = progress))
        Text(
            text = stringResource(if (ready) R.string.chats_archive_release else R.string.chats_archive_pull),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary.copy(alpha = progress),
            maxLines = 1,
        )
    }
}

private const val PULL_READY_ALPHA = 0.35f

/** What a room's cluster does for those who may manage its channels. */
class RoomChannelHandlers(
    val manages: (roomId: String) -> Boolean,
    val onAdd: (roomId: String) -> Unit,
    val onMenu: (roomId: String, channel: RoomChannel) -> Unit,
)

private fun Conversation.isRoomCluster() = kind == ConversationKind.Room && channels.isNotEmpty()

/**
 * Pinned chats: each on a capsule, all of them stuck together into one drop. With [onReorder],
 * a long press lifts a capsule out of the drop and it follows the finger; the others make way,
 * and releasing it without moving opens the menu instead.
 */
@Composable
private fun PinnedCluster(
    pinned: ImmutableList<Conversation>,
    onReorder: ((List<String>) -> Unit)?,
    onMenu: ((Conversation) -> Unit)?,
    modifier: Modifier = Modifier,
    row: @Composable (Conversation, menuInGesture: Boolean) -> Unit,
) {
    val ids = pinned.map { it.id }
    val drag = remember { PinnedDrag() }
    if (drag.lifted == null && drag.order != ids) drag.order = ids
    val byId = pinned.associateBy { it.id }
    val density = LocalDensity.current
    val radius = with(density) { Blesk.dimens.capsuleRadius.toPx() }
    val gap = with(density) { PinnedGap.toPx() }
    val lift = Blesk.colors.light.copy(alpha = if (Blesk.colors.isDark) LIFT_DARK else LIFT_LIGHT)
    Layout(
        modifier = modifier
            .fillMaxWidth()
            .metaballs(color = Blesk.colors.surface, smoothness = 16.dp) { drag.blobs(radius) },
        content = {
            drag.order.forEach { id ->
                val conversation = byId[id] ?: return@forEach
                key(id) {
                    PinnedSlot(conversation, drag, lift, onReorder, onMenu) { menuInGesture -> row(conversation, menuInGesture) }
                }
            }
        },
    ) { measurables, constraints ->
        val loose = Constraints(minWidth = constraints.maxWidth, maxWidth = constraints.maxWidth)
        val placeables = measurables.map { it.measure(loose) }
        val rowHeight = placeables.maxOfOrNull { it.height } ?: 0
        drag.step = rowHeight + gap
        drag.width = constraints.maxWidth.toFloat()
        drag.rowHeight = rowHeight.toFloat()
        val height = (rowHeight * placeables.size + gap * (placeables.size - 1).coerceAtLeast(0)).roundToInt()
        layout(constraints.maxWidth, height) { placeables.forEach { it.place(0, 0) } }
    }
}

@Composable
private fun PinnedSlot(
    conversation: Conversation,
    drag: PinnedDrag,
    lift: Color,
    onReorder: ((List<String>) -> Unit)?,
    onMenu: ((Conversation) -> Unit)?,
    content: @Composable (menuInGesture: Boolean) -> Unit,
) {
    val id = conversation.id
    val motion = Blesk.motion
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val index = drag.order.indexOf(id)
    val target = index * drag.step
    val y = remember { Animatable(Float.NaN) }
    LaunchedEffect(target, drag.step) {
        if (drag.step == 0f) return@LaunchedEffect
        if (y.value.isNaN() || motion.reduced || drag.lifted == id) y.snapTo(target) else y.animateTo(target, motion.settle())
    }
    val lifted = drag.lifted == id
    val raise by animateFloatAsState(if (lifted) 1f else 0f, motion.settle(), label = "lift")
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    val moveUp = stringResource(R.string.chats_move_up)
    val moveDown = stringResource(R.string.chats_move_down)
    val menuLabel = stringResource(R.string.chats_menu)
    val reorderable = onReorder != null && drag.order.size > 1
    Box(
        Modifier
            .zIndex(if (lifted || raise > 0f) 1f else 0f)
            .offset {
                val shown = when {
                    lifted -> target + drag.offset
                    y.value.isNaN() -> target
                    else -> y.value
                }
                drag.place(id, shown)
                IntOffset(0, shown.roundToInt())
            }
            .graphicsLayer {
                val scale = 1f + LIFT_SCALE * raise
                scaleX = scale
                scaleY = scale
            }
            .drawBehind {
                if (raise > 0f) drawRoundRect(lift.copy(alpha = lift.alpha * raise), cornerRadius = CornerRadius(size.height / 2f))
            }
            .clip(shape)
            .then(
                if (!reorderable) {
                    Modifier
                } else {
                    Modifier
                        .semantics {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction(menuLabel) { onMenu?.invoke(conversation); onMenu != null },
                                CustomAccessibilityAction(moveUp) { drag.move(id, -1, onReorder) }.takeIf { index > 0 },
                                CustomAccessibilityAction(moveDown) { drag.move(id, 1, onReorder) }.takeIf { index < drag.order.lastIndex },
                            )
                        }
                        .pointerInput(id) {
                            var travelled = 0f
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    travelled = 0f
                                    drag.offset = 0f
                                    drag.lifted = id
                                    haptics.detach()
                                },
                                onDragEnd = {
                                    val landing = drag.order.indexOf(id) * drag.step + drag.offset
                                    drag.lifted = null
                                    scope.launch {
                                        y.snapTo(landing)
                                        y.animateTo(drag.order.indexOf(id) * drag.step, motion.settle())
                                    }
                                    if (travelled < viewConfiguration.touchSlop) {
                                        onMenu?.invoke(conversation)
                                    } else {
                                        haptics.stick()
                                        onReorder?.invoke(drag.order)
                                    }
                                },
                                onDragCancel = { drag.lifted = null },
                            ) { change, amount ->
                                change.consume()
                                travelled += abs(amount.y) + abs(amount.x)
                                drag.offset += amount.y
                                if (drag.swap(id)) haptics.tick()
                            }
                        }
                },
            ),
    ) { content(reorderable) }
}

/** Where the pinned capsules are and which one is lifted; read while drawing the drop. */
@Stable
private class PinnedDrag {
    var order by mutableStateOf(emptyList<String>())
    var lifted by mutableStateOf<String?>(null)

    /** How far the lifted capsule is from its slot, following the finger. */
    var offset by mutableFloatStateOf(0f)
    var step by mutableFloatStateOf(0f)
    var width = 0f
    var rowHeight = 0f
    private val tops = HashMap<String, Float>()

    fun place(id: String, top: Float) {
        tops[id] = top
    }

    /** The lifted capsule stays out of the drop, so it reads as pulled off the cluster. */
    fun blobs(radius: Float) = order.filter { it != lifted }.mapNotNull { id ->
        tops[id]?.let { top -> Blob(Rect(0f, top, width, top + rowHeight), radius) }
    }

    /** Swaps the lifted capsule with a neighbour once it is dragged past half of it. */
    fun swap(id: String): Boolean {
        val index = order.indexOf(id)
        val half = step / 2f
        val next = when {
            offset > half && index < order.lastIndex -> index + 1
            offset < -half && index > 0 -> index - 1
            else -> return false
        }
        order = order.toMutableList().apply { add(next, removeAt(index)) }
        offset -= (next - index) * step
        return true
    }

    /** One step up or down, for TalkBack. */
    fun move(id: String, by: Int, onReorder: ((List<String>) -> Unit)?): Boolean {
        val index = order.indexOf(id)
        val next = index + by
        if (next !in order.indices || onReorder == null) return false
        order = order.toMutableList().apply { add(next, removeAt(index)) }
        onReorder(order)
        return true
    }
}

private val PinnedGap = 4.dp
private const val LIFT_SCALE = 0.03f
private const val LIFT_LIGHT = 0.7f
private const val LIFT_DARK = 0.2f

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
    val places = LocalChatRowPlaces.current
    val encryptedLabel = stringResource(R.string.chats_encrypted)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed && !onCapsule) 1f else 0f, label = "press")
    val capsule = Blesk.colors.surface
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    Box(
        modifier = modifier
            .sharedBounds(chatKey(conversation.id))
            .fillMaxWidth()
            .let { row -> if (places == null) row else row.onGloballyPositioned { places.put(conversation.id, it.boundsInWindow()) } }
            .clip(shape)
            .drawBehind { if (press > 0f) drawRect(capsule.copy(alpha = capsule.alpha * press)) }
            .combinedClickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onLongClickLabel = stringResource(R.string.chats_menu),
                onLongClick = onLongClick?.let { action ->
                    {
                        haptics.menu()
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
    val online = conversation.id in LocalOnline.current
    val onlineLabel = stringResource(R.string.chat_online_spoken)
    Row(
        modifier = modifier.semantics { if (online) stateDescription = onlineLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (conversation.kind == ConversationKind.Saved) {
            SavedAvatar()
        } else if (conversation.faces.size >= 2) {
            GroupAvatar(conversation.faces.map { it.name to it.id }, Modifier.sharedElement(avatarKey(conversation.id)))
        } else {
            Avatar(
                name = conversation.title,
                key = conversation.id,
                protected = conversation.protection == Protection.EndToEnd &&
                    conversation.kind != ConversationKind.Channel && conversation.kind != ConversationKind.Bot,
                modifier = Modifier.sharedElement(avatarKey(conversation.id)),
                online = online,
                photoFile = conversation.photo,
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
                UnreadCounter(conversation.unread, muted)
                if (conversation.unread == 0 && conversation.markedUnread) UnreadDot(muted)
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

/**
 * The unread counter: each new message flows into it as a small drop, and once the chat is
 * read the counter evaporates upwards instead of simply vanishing.
 */
@Composable
private fun UnreadCounter(count: Int, muted: Boolean) {
    val motion = Blesk.motion
    var shown by remember { mutableIntStateOf(count) }
    if (count > 0) shown = count
    val drop = remember { Animatable(1f) }
    var previous by remember { mutableIntStateOf(count) }
    LaunchedEffect(count) {
        if (count > previous && previous > 0 && !motion.reduced) {
            drop.snapTo(0f)
            drop.animateTo(1f, motion.transition())
        }
        previous = count
    }
    val dropColor = if (muted) Blesk.colors.textTertiary else Blesk.colors.accent
    AnimatedVisibility(
        visible = count > 0,
        enter = scaleIn(motion.settle(), initialScale = 0.5f) + fadeIn(motion.press()),
        exit = slideOutVertically(motion.transition()) { -it } + scaleOut(motion.transition(), targetScale = 0.5f) +
            fadeOut(motion.transition()),
    ) {
        Box(contentAlignment = Alignment.CenterStart) {
            UnreadBadge(shown, muted)
            Box(
                Modifier
                    .offset { IntOffset(-(DropTravel.toPx() * (1f - drop.value)).roundToInt(), 0) }
                    .size(6.dp)
                    .graphicsLayer { alpha = if (drop.value >= 1f) 0f else 1f - drop.value * DROP_FADE }
                    .background(dropColor, CircleShape),
            )
        }
    }
}

private val DropTravel = 22.dp
private const val DROP_FADE = 0.6f

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

/** Shared keys that carry a chat row into its open chat and back. */
internal fun chatKey(id: String) = "chat:$id"
internal fun avatarKey(id: String) = "avatar:$id"

/** A pinned room held by the finger: the order of the pinned rooms and how far the held one has moved. */
private class RoomDrag {
    var order by mutableStateOf(emptyList<String>())
    var lifted by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    var moved = false
}

/**
 * Long press lifts a pinned room and it follows the finger, the others making way; releasing it
 * without moving opens the menu instead, as with pinned chats.
 */
private fun Modifier.roomDrag(
    id: String,
    drag: RoomDrag,
    list: LazyListState,
    haptics: Haptics,
    onMenu: (() -> Unit)?,
    onDrop: (List<String>) -> Unit,
): Modifier = graphicsLayer {
    if (drag.lifted == id) {
        translationY = drag.offset
        scaleX = ROOM_LIFT
        scaleY = ROOM_LIFT
    }
}.pointerInput(id) {
    fun size(key: String) = list.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }?.size?.toFloat()
    detectDragGesturesAfterLongPress(
        onDragStart = {
            drag.lifted = id
            drag.offset = 0f
            drag.moved = false
            haptics.detach()
        },
        onDragEnd = {
            drag.lifted = null
            drag.offset = 0f
            if (drag.moved) onDrop(drag.order) else onMenu?.invoke()
            haptics.stick()
        },
        onDragCancel = {
            drag.lifted = null
            drag.offset = 0f
        },
    ) { change, amount ->
        change.consume()
        drag.offset += amount.y
        val index = drag.order.indexOf(id)
        val next = drag.order.getOrNull(index + 1)
        val previous = drag.order.getOrNull(index - 1)
        val nextSize = next?.let(::size)
        val previousSize = previous?.let(::size)
        // Past half of a neighbour, the held room takes its place and the offset is carried over.
        when {
            next != null && nextSize != null && drag.offset > nextSize / 2 -> {
                drag.order = drag.order.toMutableList().apply { add(index + 1, removeAt(index)) }
                drag.offset -= nextSize
                drag.moved = true
                haptics.tick()
            }
            previous != null && previousSize != null && drag.offset < -previousSize / 2 -> {
                drag.order = drag.order.toMutableList().apply { add(index - 1, removeAt(index)) }
                drag.offset += previousSize
                drag.moved = true
                haptics.tick()
            }
        }
    }
}

private const val ROOM_LIFT = 1.02f
