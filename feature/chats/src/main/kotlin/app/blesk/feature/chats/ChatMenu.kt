package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import java.time.Instant
import kotlin.math.roundToInt

/** What can be done to a chat from the list. */
class ChatMenuActions(
    val onPin: (Boolean) -> Unit,
    val onArchive: (Boolean) -> Unit,
    /** Epoch millis; [Long.MAX_VALUE] for good, 0 to turn sound back on. */
    val onMute: (Long) -> Unit,
    val onMarkUnread: (Boolean) -> Unit,
)

/**
 * Where each chat row is on screen, noted as the rows are placed, so the menu can lift the
 * very row that was pressed. Not state: it is only read at the moment of the long press.
 */
class ChatRowPlaces {
    private val places = HashMap<String, Rect>()

    fun put(id: String, bounds: Rect) {
        places[id] = bounds
    }

    operator fun get(id: String): Rect? = places[id]
}

val LocalChatRowPlaces = staticCompositionLocalOf<ChatRowPlaces?> { null }

/**
 * Opened by a long press on a chat. The row itself rises in place as a clear capsule over the
 * darkened list, with the menu stuck under it (over it, near the bottom); its text never moves.
 * Without a known place, as for a room, the menu is a matte sheet at the bottom.
 */
@Composable
fun ChatMenu(
    conversation: Conversation,
    now: Long,
    actions: ChatMenuActions,
    onDismiss: () -> Unit,
    anchor: Rect? = null,
) {
    var muting by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }
    val items: @Composable () -> Unit = {
        Card {
            if (muting) {
                MuteChoices(now) { until -> act { actions.onMute(until) } }
                return@Card
            }
            ListRow(
                title = stringResource(if (conversation.pinned) R.string.chats_unpin else R.string.chats_pin),
                icon = BleskIcons.Pin,
                showChevron = false,
                onClick = {
                    if (!conversation.pinned) haptics.stick()
                    act { actions.onPin(!conversation.pinned) }
                },
            )
            ListRow(
                title = stringResource(if (conversation.archived) R.string.chats_from_archive else R.string.chats_to_archive),
                icon = BleskIcons.Archive,
                showChevron = false,
                onClick = { act { actions.onArchive(!conversation.archived) } },
            )
            // Notes to yourself never ring, so there is nothing to silence.
            if (conversation.kind != ConversationKind.Saved) {
                if (conversation.muted(now)) {
                    ListRow(
                        title = stringResource(R.string.chats_unmute),
                        icon = BleskIcons.Bell,
                        showChevron = false,
                        onClick = { act { actions.onMute(0) } },
                    )
                } else {
                    ListRow(title = stringResource(R.string.chats_mute), icon = BleskIcons.BellOff, onClick = { muting = true })
                }
            }
            val unread = conversation.unread > 0 || conversation.markedUnread
            ListRow(
                title = stringResource(if (unread) R.string.chats_mark_read else R.string.chats_mark_unread),
                icon = BleskIcons.Check,
                showChevron = false,
                onClick = { act { actions.onMarkUnread(!unread) } },
            )
        }
    }
    if (anchor == null) {
        MenuSheet(title = conversation.title, onDismiss = onDismiss) { items() }
    } else {
        LiftedRow(anchor, onDismiss, menu = items) {
            ConversationSummary(conversation, typing = false, now = Instant.ofEpochMilli(now), Modifier.padding(RowPadding))
        }
    }
}

/**
 * The pressed row raised where it lies: the same content at the same place and size, now a
 * capsule with a shadow, so it reads clearly over the dark. The menu flows out under it.
 */
@Composable
private fun LiftedRow(anchor: Rect, onDismiss: () -> Unit, menu: @Composable () -> Unit, row: @Composable () -> Unit) {
    BackHandler(onBack = onDismiss)
    val motion = Blesk.motion
    val rise = remember { Animatable(if (motion.reduced) 1f else 0f) }
    LaunchedEffect(Unit) { rise.animateTo(1f, motion.settle()) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(Blesk.dimens.capsuleRadius)
    val surface = Blesk.colors.surface
    val gap = with(LocalDensity.current) { MenuGap.roundToPx() }
    val insets = WindowInsets.systemBars
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
            .drawBehind { drawRect(Color.Black.copy(alpha = LIFT_SCRIM * rise.value.coerceIn(0f, 1f))) }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Layout(
            content = {
                Box(
                    Modifier
                        .graphicsLayer {
                            val scale = 1f + LIFT_SCALE * rise.value
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = LiftShadow.toPx() * rise.value
                            this.shape = shape
                            clip = true
                        }
                        .background(surface)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) { row() }
                Box(
                    Modifier
                        .graphicsLayer {
                            alpha = rise.value.coerceIn(0f, 1f)
                            scaleY = MENU_FROM + (1f - MENU_FROM) * rise.value
                            transformOrigin = TransformOrigin(0.5f, 0f)
                        }
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                ) { menu() }
            },
        ) { measurables, constraints ->
            val width = anchor.width.roundToInt().coerceAtMost(constraints.maxWidth)
            val rowPlaceable = measurables[0].measure(Constraints.fixed(width, anchor.height.roundToInt()))
            val menuPlaceable = measurables[1].measure(Constraints(maxWidth = width, maxHeight = constraints.maxHeight))
            layout(constraints.maxWidth, constraints.maxHeight) {
                val left = (anchor.left - origin.x).roundToInt()
                val top = (anchor.top - origin.y).roundToInt()
                rowPlaceable.place(left, top)
                val below = top + rowPlaceable.height + gap
                val floor = constraints.maxHeight - insets.getBottom(this@Layout) - gap
                val menuTop = if (below + menuPlaceable.height <= floor) {
                    below
                } else {
                    (top - gap - menuPlaceable.height).coerceAtLeast(insets.getTop(this@Layout))
                }
                menuPlaceable.place(left, menuTop)
            }
        }
    }
}

/** A matte sheet at the bottom over a dimmed screen, titled with what it acts on; a tap outside or a swipe down closes it. */
@Composable
internal fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .dragToDismiss(onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Text(
                text = title,
                style = Blesk.type.headline,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun MuteChoices(now: Long, onChoose: (Long) -> Unit) {
    listOf(
        R.string.chats_mute_hour to now + HOUR,
        R.string.chats_mute_eight to now + 8 * HOUR,
        R.string.chats_mute_two_days to now + 48 * HOUR,
        R.string.chats_mute_forever to Long.MAX_VALUE,
    ).forEach { (label, until) ->
        ListRow(title = stringResource(label), icon = BleskIcons.BellOff, showChevron = false, onClick = { onChoose(until) })
    }
}

private const val SCRIM = 0.35f
private const val LIFT_SCRIM = 0.45f
private const val LIFT_SCALE = 0.02f
private const val MENU_FROM = 0.85f
private val LiftShadow = 16.dp
private val MenuGap = 8.dp
private val RowPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
private const val HOUR = 3_600_000L
