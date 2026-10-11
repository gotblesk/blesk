package app.blesk.feature.chats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.lerp
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.media.MediaContent
import app.blesk.feature.chats.media.isVisual
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val BubbleRadius = 20.dp
private val BubbleShape = RoundedCornerShape(BubbleRadius)
private val QuoteGap = 12.dp
private val ReplySwipe = 64.dp

/** Callbacks a message can trigger. */
internal class MessageActions(
    val onReply: (Message) -> Unit,
    val onMenu: (Message) -> Unit,
    val onReact: (Message, String) -> Unit,
    val onApplyTheme: (ThemeSpec) -> Unit,
    val onButton: (Message, BotButton) -> Unit = { _, _ -> },
)

/**
 * Consecutive messages of one author drawn as one liquid column: every bubble, reply quote,
 * thread to the quote and reaction is a drop of the same metaball field.
 */
@Composable
internal fun MessageColumn(
    messages: ImmutableList<Message>,
    author: String?,
    fresh: Set<String>,
    quoteText: (String) -> String?,
    actions: MessageActions,
    modifier: Modifier = Modifier,
) {
    val own = messages.first().isOwn
    val parts = remember { PartRegistry() }
    val growth = remember { HashMap<String, Animatable<Float, *>>() }
    val density = LocalDensity.current
    val radius = with(density) { BubbleRadius.toPx() }
    val color = if (own) Blesk.colors.surfaceStrong else Blesk.colors.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .onPlaced { parts.root = it }
            .metaballs(color = color, smoothness = 14.dp) {
                parts.blobs(radius, 5.dp.toPx(), 28.dp.toPx(), own) { id -> growth[id]?.value ?: 1f }
            },
        horizontalAlignment = if (own) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (author != null) {
            Text(
                text = author,
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
        messages.forEach { message ->
            val grow = growth.getOrPut(message.id) { Animatable(if (message.id in fresh) 0f else 1f) }
            LaunchedEffect(message.id) {
                if (grow.value < 1f) grow.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
            }
            MessageEntry(
                message = message,
                own = own,
                quote = message.replyToId?.let(quoteText),
                parts = parts,
                contentAlpha = { grow.value },
                actions = actions,
            )
        }
    }
}

/** Positions of the drops inside one column, collected while placing. */
private class PartRegistry {
    var root: LayoutCoordinates? = null
    private val coordinates = LinkedHashMap<String, LayoutCoordinates>()
    fun put(key: String, layout: LayoutCoordinates) {
        coordinates[key] = layout
    }

    private fun rect(key: String): Rect? {
        val origin = root ?: return null
        val child = coordinates[key]?.takeIf { it.isAttached } ?: return null
        if (!origin.isAttached) return null
        return origin.localBoundingBoxOf(child, clipBounds = false)
    }

    fun blobs(radius: Float, thread: Float, dots: Float, own: Boolean, growth: (String) -> Float): List<Blob> {
        val result = ArrayList<Blob>()
        coordinates.keys.filter { it.startsWith("bubble:") }.forEach { key ->
            val id = key.removePrefix("bubble:")
            val bubble = rect(key) ?: return@forEach
            val full = Blob(bubble, radius)
            val g = growth(id)
            if (g < 1f) {
                // A message that was being typed grows out of the typing dots.
                val start = if (own) bubble.right - dots * 2f else bubble.left
                val seed = Blob(Rect(start, bubble.bottom - dots, start + dots * 2f, bubble.bottom), dots / 2f)
                result += lerp(seed, full, g)
            } else {
                result += full
            }
            rect("quote:$id")?.let { quote ->
                result += Blob(quote, radius)
                val x = if (own) quote.right - radius else quote.left + radius
                result += Blob(Rect(x - thread / 2f, quote.bottom - radius / 2f, x + thread / 2f, bubble.top + radius / 2f), thread / 2f)
            }
            rect("reactions:$id")?.let { result += Blob(it, it.height / 2f) }
        }
        coordinates.keys.filter { it.startsWith("circle:") }.forEach { key ->
            rect(key)?.let { result += Blob(it, minOf(it.width, it.height) / 2f) }
            rect("reactions:${key.removePrefix("circle:")}")?.let { result += Blob(it, it.height / 2f) }
        }
        return result
    }
}

@Composable
private fun MessageEntry(
    message: Message,
    own: Boolean,
    quote: String?,
    parts: PartRegistry,
    contentAlpha: () -> Float,
    actions: MessageActions,
) {
    val replyLabel = stringResource(R.string.chat_reply)
    val menuLabel = stringResource(R.string.chat_copy)
    val time = timeFormat.format(message.sentAt.atZone(ZoneId.systemDefault()))
    val status = if (own) stringResource(statusLabel(message.status)) else null
    val edited = if (message.edited) stringResource(R.string.chat_edited) else null
    val density = LocalDensity.current
    val swipeLimit = with(density) { ReplySwipe.toPx() }
    val drag = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Column(
        horizontalAlignment = if (own) Alignment.End else Alignment.Start,
        modifier = Modifier
            .offset { IntOffset(drag.value.roundToInt(), 0) }
            .pointerInput(message.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(drag.value) >= swipeLimit) actions.onReply(message)
                        scope.launch { drag.animateTo(0f, spring(dampingRatio = 0.8f)) }
                    },
                    onDragCancel = { scope.launch { drag.animateTo(0f) } },
                ) { change, amount ->
                    change.consume()
                    val next = drag.value + amount
                    // Pull towards the middle of the screen only, with resistance past the threshold.
                    val bounded = if (own) next.coerceIn(-swipeLimit * 1.4f, 0f) else next.coerceIn(0f, swipeLimit * 1.4f)
                    scope.launch { drag.snapTo(bounded) }
                }
            },
    ) {
        if (quote != null) {
            Text(
                text = quote,
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                maxLines = 1,
                modifier = Modifier
                    .padding(bottom = QuoteGap)
                    .widthIn(max = 220.dp)
                    .onPlaced { parts.put("quote:${message.id}", it) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
        val attachment = message.attachment
        val circle = attachment is Attachment.Circle
        val label = LocalContext.current.resources.attachmentLabel(attachment)
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .onPlaced { parts.put(if (circle) "circle:${message.id}" else "bubble:${message.id}", it) }
                .flashOn(message.status.takeIf { own }, if (circle) CircleShape else BubbleShape)
                .combinedClickable(
                    onClickLabel = null,
                    onLongClickLabel = menuLabel,
                    onLongClick = { actions.onMenu(message) },
                    onClick = {},
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = listOfNotNull(label.ifEmpty { null }, message.text.ifEmpty { null }, edited, time, status)
                        .joinToString(", ")
                    customActions = listOf(
                        CustomAccessibilityAction(replyLabel) { actions.onReply(message); true },
                    )
                }
                .graphicsLayer { alpha = contentAlpha() }
                .padding(
                    horizontal = if (circle || attachment?.isVisual() == true) 4.dp else 14.dp,
                    vertical = if (circle || attachment?.isVisual() == true) 4.dp else 9.dp,
                ),
        ) {
            if (attachment is Attachment.Circle) {
                MediaContent(message, attachment)
                Stamp(time, status, edited, Modifier.align(Alignment.BottomEnd).padding(6.dp), onMedia = true)
                return@Box
            }
            Column {
                if (message.forwardedFrom != null) {
                    Row(
                        modifier = Modifier.padding(bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(BleskIcons.Forward, contentDescription = null, size = 14.dp, tint = Blesk.colors.textTertiary)
                        Text(stringResource(R.string.chat_forwarded), style = Blesk.type.caption, color = Blesk.colors.textTertiary)
                    }
                }
                when (attachment) {
                    is Attachment.Theme -> ThemeCard(attachment.spec, actions.onApplyTheme)
                    null -> Unit
                    else -> MediaContent(message, attachment)
                }
                if (message.text.isNotEmpty()) {
                    val inset = if (attachment?.isVisual() == true) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier
                    Text(withMentions(message.text), modifier = inset)
                }
                Stamp(
                    time, status, edited,
                    Modifier
                        .align(Alignment.End)
                        .padding(top = 2.dp)
                        .padding(horizontal = if (attachment?.isVisual() == true) 8.dp else 0.dp),
                )
            }
        }
        if (message.reactions.isNotEmpty()) {
            Reactions(
                reactions = message.reactions,
                onToggle = { actions.onReact(message, it) },
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .offset(y = (-6).dp)
                    .onPlaced { parts.put("reactions:${message.id}", it) },
            )
        }
        if (message.buttons.isNotEmpty()) BotButtons(message, actions)
    }
}

/** Inline buttons under a bot message: matte, never glinting, since bots are not encrypted. */
@Composable
private fun BotButtons(message: Message, actions: MessageActions) {
    Column(Modifier.padding(top = 6.dp).widthIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        message.buttons.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { button ->
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = Blesk.dimens.touchTarget)
                            .clip(CircleShape)
                            .background(Blesk.colors.surfaceStrong)
                            .clickable(role = Role.Button) { actions.onButton(message, button) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(button.text, style = Blesk.type.label, maxLines = 1)
                        if (button.url != null) {
                            Icon(
                                BleskIcons.ChevronRight,
                                contentDescription = null,
                                size = 14.dp,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Time, edit mark and delivery status. Over media it sits on a dark pill. */
@Composable
private fun Stamp(time: String, status: String?, edited: String?, modifier: Modifier = Modifier, onMedia: Boolean = false) {
    val color = if (onMedia) Color.White else Blesk.colors.textTertiary
    Row(
        modifier = if (onMedia) {
            modifier.background(Color.Black.copy(alpha = 0.45f), CircleShape).padding(horizontal = 8.dp, vertical = 2.dp)
        } else {
            modifier
        },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (edited != null) Text(edited, style = Blesk.type.caption, color = color)
        Text(time, style = Blesk.type.caption, color = color)
        if (status != null) Text(status, style = Blesk.type.caption, color = color)
    }
}

@Composable
private fun Reactions(reactions: ImmutableList<Reaction>, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        reactions.forEach { reaction ->
            val label = stringResource(R.string.chat_reaction, reaction.symbol, reaction.count)
            val ring by animateFloatAsState(if (reaction.mine) 1f else 0f, label = "mine")
            Row(
                modifier = Modifier
                    .border(1.dp, Blesk.colors.light.copy(alpha = 0.5f * ring), CircleShape)
                    .clickable { onToggle(reaction.symbol) }
                    .semantics(mergeDescendants = true) { contentDescription = label }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(reaction.symbol, style = Blesk.type.label)
                if (reaction.count > 1) Text(reaction.count.toString(), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
            }
        }
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

internal fun statusLabel(status: SendStatus) = when (status) {
    SendStatus.Failed -> R.string.chat_status_failed
    SendStatus.Sending -> R.string.chat_status_sending
    SendStatus.Sent -> R.string.chat_status_sent
    SendStatus.Delivered -> R.string.chat_status_delivered
    SendStatus.Read -> R.string.chat_status_read
}

private val mention = Regex("(?<![\\w@])@[a-z0-9_]{3,32}")

/** @usernames stand out, so people notice they are mentioned. */
private fun withMentions(text: String): AnnotatedString = buildAnnotatedString {
    var from = 0
    mention.findAll(text).forEach { match ->
        append(text.substring(from, match.range.first))
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(match.value) }
        from = match.range.last + 1
    }
    append(text.substring(from))
}
