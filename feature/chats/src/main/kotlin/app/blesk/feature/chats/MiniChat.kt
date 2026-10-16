package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.GroupAvatar
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.component.dismissOnTap
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.locks.LockCapsule
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A look into a chat without opening it: the pressed row flows out on a metaball neck into a
 * window about 70 % of the screen tall, rim-lit like a lens, over the blurred, darkened list. It
 * holds a small header and the latest messages in the same bubbles and columns as the chat; they
 * scroll, and nothing is marked read. Under it, on a bridge, the actions stick together in a
 * cluster. A tap on the window pours it out into the chat; a tap beside it, a swipe down on its
 * header or «назад» draw it back into the row.
 */
@Composable
internal fun MiniChat(
    conversation: Conversation,
    anchor: Rect,
    messages: ImmutableList<Message>,
    authorName: (String) -> String?,
    actions: List<ClusterAction>,
    onOpen: (() -> Unit)?,
    onDismiss: () -> Unit,
    /** A locked chat shows its lock, never its messages, until it is opened. */
    locked: Boolean = false,
) {
    val motion = Blesk.motion
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val grow = remember { Animatable(if (motion.reduced) 1f else 0f) }
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { grow.animateTo(1f, motion.stretch()) }
    fun close(then: () -> Unit = {}) {
        if (closing) return
        closing = true
        scope.launch {
            grow.animateTo(0f, motion.transition())
            onDismiss()
            then()
        }
    }
    BackHandler { close() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val fill = Blesk.colors.background
    val rim = Blesk.colors.light
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) + with(density) { WindowTop.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            // The list behind darkens as the window grows, so the window stands out of it.
            .drawBehind { drawRect(Color.Black.copy(alpha = SCRIM * grow.value.coerceIn(0f, 1f))) }
            .dismissOnTap({ close() }),
    ) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val gutter = with(density) { Blesk.dimens.gutter.toPx() }
        val windowHeight = height * WINDOW_SHARE
        val target = Rect(gutter, top, width - gutter, top + windowHeight)
        val row = anchor.translate(-origin.x, -origin.y)
        val clusterTop = target.bottom + with(density) { ClusterGap.toPx() }
        fun current(p: Float) = Rect(
            lerp(row.left, target.left, p),
            lerp(row.top, target.top, p),
            lerp(row.right, target.right, p),
            lerp(row.bottom, target.bottom, p),
        )
        // One liquid drop: the row it comes from, the window, and the necks to the row and to the actions.
        Box(
            Modifier
                .fillMaxSize()
                .metaballs(fill, smoothness = NeckSmoothness) {
                    val p = grow.value.coerceIn(0f, 1f)
                    val window = current(p)
                    val corner = lerp(row.height / 2f, WindowCorner.toPx(), p)
                    buildList {
                        add(Blob(window, corner))
                        val left = 1f - p
                        if (left > 0f) {
                            val shrunk = Rect(
                                row.center.x - row.width / 2f * left,
                                row.center.y - row.height / 2f * left,
                                row.center.x + row.width / 2f * left,
                                row.center.y + row.height / 2f * left,
                            )
                            add(Blob(shrunk, shrunk.height / 2f))
                        }
                        if (p > NECK_FROM) {
                            val neck = Neck.toPx() * ((p - NECK_FROM) / (1f - NECK_FROM))
                            add(Blob(Rect(window.center.x - neck, window.bottom - corner, window.center.x + neck, clusterTop + neck), neck))
                        }
                    }
                },
        )
        val shown = ((grow.value - CONTENT_FROM) / (1f - CONTENT_FROM)).coerceIn(0f, 1f)
        val window = current(grow.value.coerceIn(0f, 1f))
        val openLabel = stringResource(R.string.mini_chat_open)
        Column(
            Modifier
                .offset { IntOffset(window.left.roundToInt(), window.top.roundToInt()) }
                .size(with(density) { window.width.toDp() }, with(density) { window.height.toDp() })
                .graphicsLayer { alpha = shown }
                .clip(RoundedCornerShape(WindowCorner))
                .drawWithContent {
                    drawContent()
                    // A light rim, like the lenses'.
                    drawRoundRect(
                        color = rim.copy(alpha = RIM_ALPHA * shown),
                        cornerRadius = CornerRadius(WindowCorner.toPx()),
                        style = Stroke(1.dp.toPx()),
                    )
                }
                .let { window ->
                    if (onOpen == null) return@let window.blockTaps()
                    window.clickable(role = Role.Button, onClickLabel = openLabel) { close(onOpen) }
                },
        ) {
            MiniHeader(conversation, Modifier.pointerInput(Unit) {
                detectVerticalDragGestures { change, drag -> if (drag > SWIPE_CLOSE) close() else change.consume() }
            })
            if (locked) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { LockCapsule(dimmed = false) }
                return@Column
            }
            val runs = remember(messages) { runsOf(messages) }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Bottom),
            ) {
                items(runs.asReversed(), key = { it.first().id }) { run ->
                    val own = run.first().isOwn
                    MessageColumn(
                        messages = run,
                        author = if (!own && conversation.faces.size >= 2) authorName(run.first().authorId) else null,
                        fresh = emptySet(),
                        quoteText = { id -> messages.firstOrNull { it.id == id }?.text },
                        actions = MessageActions(onReply = {}, onMenu = {}, onReact = { _, _ -> }, onApplyTheme = {}),
                    )
                }
            }
        }
        val clusterShown = ((grow.value - CLUSTER_FROM) / (1f - CLUSTER_FROM)).coerceIn(0f, 1f)
        Box(
            Modifier
                .offset { IntOffset(target.left.roundToInt(), clusterTop.roundToInt()) }
                .size(with(density) { target.width.toDp() }, ClusterHeight)
                .graphicsLayer {
                    alpha = clusterShown
                    translationY = (1f - clusterShown) * -ClusterGap.toPx()
                }
                .blockTaps(),
        ) {
            ActionCluster(
                actions.map { action ->
                    ClusterAction(action.icon, action.label) {
                        haptics.tick()
                        action.onClick()
                    }
                },
            )
        }
    }
}

@Composable
private fun MiniHeader(conversation: Conversation, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (conversation.faces.size >= 2) {
            GroupAvatar(faces = conversation.faces.map { it.name to it.id }, size = 32.dp)
        } else {
            Avatar(name = conversation.title, key = conversation.id, size = 32.dp, photoFile = conversation.photo)
        }
        Column(Modifier.weight(1f)) {
            Text(conversation.title, style = Blesk.type.bodyStrong, maxLines = 1)
            val count = conversation.memberCount
            val line = if (count > 0) pluralStringResource(R.plurals.chats_members, count, count) else "@${conversation.id}"
            Text(line, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
        }
    }
}

/** Messages in a row from one author, the way the chat groups them into columns. */
private fun runsOf(messages: List<Message>): List<ImmutableList<Message>> {
    val runs = ArrayList<ImmutableList<Message>>()
    var current = ArrayList<Message>()
    messages.forEach { message ->
        if (current.isNotEmpty() && current.last().authorId != message.authorId) {
            runs += current.toImmutableList()
            current = ArrayList()
        }
        current += message
    }
    if (current.isNotEmpty()) runs += current.toImmutableList()
    return runs
}

private val WindowTop = 56.dp
private val WindowCorner = 36.dp
private val NeckSmoothness = 24.dp
private val Neck = 10.dp
private val ClusterGap = 14.dp
private val ClusterHeight = 72.dp
private const val WINDOW_SHARE = 0.7f
private const val NECK_FROM = 0.6f
private const val CONTENT_FROM = 0.45f
private const val CLUSTER_FROM = 0.7f
private const val RIM_ALPHA = 0.35f
private const val SCRIM = 0.4f
private const val SWIPE_CLOSE = 24f
