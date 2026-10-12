package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.Markup
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.component.rememberCopy
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.OnOwnBubble
import kotlinx.coroutines.delay

/** Quick reactions offered in the message menu. User content, not interface icons. */
internal val QuickReactions = listOf("❤️", "👍", "🔥", "😂", "😢")

/** A matte sheet with reactions and actions for one message. */
@Composable
internal fun MessageMenu(
    message: Message,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReact: ((String) -> Unit)?,
    editable: Boolean = true,
    onForward: (() -> Unit)? = null,
    onPin: (() -> Unit)? = null,
    pinned: Boolean = false,
    onSelect: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null,
) {
    val copy = rememberCopy()
    val closeLabel = stringResource(R.string.chat_close)
    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = closeLabel,
                onClick = onDismiss,
            ),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .dragToDismiss(onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LiftedMessage(message, Modifier.flowIn(order = 0).align(if (message.isOwn) Alignment.End else Alignment.Start))
            if (onReact != null) {
                Row(
                    modifier = Modifier
                        .flowIn(order = 1)
                        .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
                        .background(Blesk.colors.surface)
                        .padding(6.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    (listOf(GLINT_REACTION) + QuickReactions).forEach { symbol ->
                        val glint = symbol == GLINT_REACTION
                        val name = if (glint) stringResource(R.string.chat_glint) else symbol
                        val label = stringResource(R.string.chat_react, name)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable(role = Role.Button) { act { onReact(symbol) } }
                                .semantics { contentDescription = label },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (glint) {
                                Icon(BleskIcons.Spark, contentDescription = null, tint = Blesk.colors.textPrimary)
                            } else {
                                Text(symbol, style = Blesk.type.headline)
                            }
                        }
                    }
                }
            }
            Card(Modifier.flowIn(order = 2)) {
                onRetry?.let { retry ->
                    ListRow(
                        title = stringResource(R.string.chat_retry),
                        icon = BleskIcons.Send,
                        showChevron = false,
                        onClick = { act(retry) },
                    )
                }
                ListRow(
                    title = stringResource(R.string.chat_reply),
                    icon = BleskIcons.Back,
                    showChevron = false,
                    onClick = { act(onReply) },
                )
                ListRow(
                    title = stringResource(R.string.chat_copy),
                    icon = BleskIcons.Copy,
                    showChevron = false,
                    onClick = {
                        act {
                            copy(message.text, false)
                        }
                    },
                )
                onForward?.let { forward ->
                    ListRow(
                        title = stringResource(R.string.chat_forward),
                        icon = BleskIcons.Forward,
                        showChevron = false,
                        onClick = { act(forward) },
                    )
                }
                onPin?.let { pin ->
                    ListRow(
                        title = stringResource(if (pinned) R.string.chat_unpin else R.string.chat_pin),
                        icon = BleskIcons.Pin,
                        showChevron = false,
                        onClick = { act(pin) },
                    )
                }
                onSelect?.let { select ->
                    ListRow(
                        title = stringResource(R.string.chat_select),
                        icon = BleskIcons.Check,
                        showChevron = false,
                        onClick = { act(select) },
                    )
                }
                if (message.isOwn && editable) {
                    ListRow(
                        title = stringResource(R.string.chat_edit),
                        icon = BleskIcons.Edit,
                        showChevron = false,
                        onClick = { act(onEdit) },
                    )
                    ListRow(
                        title = stringResource(R.string.chat_delete),
                        icon = BleskIcons.Trash,
                        showChevron = false,
                        onClick = { act(onDelete) },
                    )
                }
            }
        }
    }
}

/** The pressed message, lifted above the dimmed chat so it is clear what the menu is for. */
@Composable
private fun LiftedMessage(message: Message, modifier: Modifier = Modifier) {
    val text = Markup.parse(message.text).masked(stringResource(R.string.chat_spoiler))
    if (text.isBlank()) return
    Box(
        modifier
            .widthIn(max = 300.dp)
            .shadow(LIFT_ELEVATION, BubbleShape, clip = false)
            .background(if (message.isOwn) Blesk.colors.bubbleOwn else Blesk.colors.surface, BubbleShape)
            .padding(horizontal = 14.dp, vertical = 9.dp)
            .clearAndSetSemantics { },
    ) {
        if (message.isOwn) OnOwnBubble { Text(text, maxLines = LIFT_MAX_LINES) } else Text(text, maxLines = LIFT_MAX_LINES)
    }
}

private val LIFT_ELEVATION = 12.dp
private const val LIFT_MAX_LINES = 6

/**
 * The menu flows out of the pressed message: each capsule rises and swells from below, one
 * after another. Under reduced motion it simply appears.
 */
@Composable
private fun Modifier.flowIn(order: Int): Modifier {
    val reduced = Blesk.motion.reduced
    val progress = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        delay(order * FLOW_STAGGER_MS)
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    return graphicsLayer {
        val t = progress.value
        alpha = t.coerceIn(0f, 1f)
        val scale = FLOW_START_SCALE + (1f - FLOW_START_SCALE) * t
        scaleX = scale
        scaleY = scale
        translationY = (1f - t) * FLOW_RISE_DP.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, 1f)
    }
}

private const val FLOW_STAGGER_MS = 45L
private const val FLOW_START_SCALE = 0.86f
private const val FLOW_RISE_DP = 24
