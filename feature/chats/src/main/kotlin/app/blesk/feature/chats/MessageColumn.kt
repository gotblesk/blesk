package app.blesk.feature.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Markup
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Reaction
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.ensureContrast
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.component.rememberAvatarInk
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.fire.lightSweep
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.lerp
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.OnOwnBubble
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.media.MediaContent
import app.blesk.feature.chats.media.isVisual
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val BubbleRadius = 20.dp
internal val BubbleShape = RoundedCornerShape(BubbleRadius)
private val QuoteGap = 12.dp
private val ReplySwipe = 64.dp

/** Callbacks a message can trigger. */
internal class MessageActions(
    val onReply: (Message) -> Unit,
    val onMenu: (Message) -> Unit,
    val onReact: (Message, String) -> Unit,
    val onApplyTheme: (ThemeSpec) -> Unit,
    val onButton: (Message, BotButton) -> Unit = { _, _ -> },
    /** A plain tap; it picks messages while some are selected. */
    val onTap: (Message) -> Unit = {},
    /** The quick reaction on a double tap; null where reactions are not offered. */
    val onDoubleTap: ((Message) -> Unit)? = null,
    /** A press on an @username in the text; null where people cannot be opened. */
    val onMention: ((String) -> Unit)? = null,
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
    selection: Set<String> = emptySet(),
    /** A message a search led to; light passes over it once. */
    flashId: String? = null,
) {
    val own = messages.first().isOwn
    val parts = remember { PartRegistry() }
    val growth = remember { HashMap<String, Animatable<Float, *>>() }
    val density = LocalDensity.current
    val radius = with(density) { BubbleRadius.toPx() }
    val color = if (own) Blesk.colors.bubbleOwn else Blesk.colors.surface

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
                color = rememberAvatarInk(messages.first().authorId),
                modifier = Modifier.padding(start = 14.dp, bottom = 2.dp),
            )
        }
        messages.forEach { message ->
            val grow = growth.getOrPut(message.id) { Animatable(if (message.id in fresh) 0f else 1f) }
            LaunchedEffect(message.id) {
                if (grow.value < 1f) grow.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
            }
            // A message that would not go comes away from the column, leaving a gap.
            if (own && message.status == SendStatus.Failed) Spacer(Modifier.height(FailedGap))
            SelectableRow(
                selecting = selection.isNotEmpty(),
                selected = message.id in selection,
                own = own,
                onToggle = { actions.onTap(message) },
            ) {
                val entry: @Composable () -> Unit = {
                    MessageEntry(
                        message = message,
                        own = own,
                        quote = message.replyToId?.let(quoteText),
                        parts = parts,
                        contentAlpha = { grow.value },
                        actions = actions,
                        selected = message.id in selection,
                        selecting = selection.isNotEmpty(),
                        flashing = message.id == flashId,
                    )
                }
                if (own) OnOwnBubble(entry) else entry()
            }
        }
    }
}

/**
 * In selection mode a circle slides in from the left of every message and the messages shift
 * over to make room; the circle fills in for the picked ones.
 */
@Composable
private fun SelectableRow(selecting: Boolean, selected: Boolean, own: Boolean, onToggle: () -> Unit, content: @Composable () -> Unit) {
    val motion = Blesk.motion
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(
            visible = selecting,
            enter = expandHorizontally(motion.settle()) + fadeIn(motion.press()),
            exit = shrinkHorizontally(motion.transition()) + fadeOut(motion.press()),
        ) {
            val fill by animateColorAsState(if (selected) Blesk.colors.accent else Color.Transparent, motion.select(), label = "pick")
            Box(
                Modifier
                    .padding(end = 10.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .border(1.5.dp, if (selected) Blesk.colors.accent else Blesk.colors.textTertiary, CircleShape)
                    .clickable(onClick = onToggle)
                    .clearAndSetSemantics { },
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(BleskIcons.Check, contentDescription = null, size = 14.dp, tint = Blesk.colors.onAccent)
            }
        }
        Box(Modifier.weight(1f), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) { content() }
    }
}

/** Positions of the drops inside one column, collected while placing. */
private class PartRegistry {
    var root: LayoutCoordinates? = null
    private val coordinates = LinkedHashMap<String, LayoutCoordinates>()

    // Where each drop was last placed, as state: when a drop moves or grows (a reaction added,
    // a message edited) the column is drawn again instead of keeping the old shape.
    private val placed = mutableStateMapOf<String, Pair<Offset, IntSize>>()

    fun put(key: String, layout: LayoutCoordinates) {
        coordinates[key] = layout
        val now = layout.positionInRoot() to layout.size
        if (placed[key] != now) placed[key] = now
    }

    private fun rect(key: String): Rect? {
        placed[key] ?: return null
        val origin = root ?: return null
        val child = coordinates[key]?.takeIf { it.isAttached } ?: return null
        if (!origin.isAttached) return null
        return origin.localBoundingBoxOf(child, clipBounds = false)
    }

    /** Every reaction is its own capsule; side by side they stick into one cluster on the message's edge. */
    private fun reactionBlobs(id: String, into: MutableList<Blob>) {
        coordinates.keys.filter { it.startsWith("reaction:$id:") }.forEach { key ->
            rect(key)?.let { into += Blob(it, it.height / 2f) }
        }
    }

    fun blobs(radius: Float, thread: Float, dots: Float, own: Boolean, growth: (String) -> Float): List<Blob> {
        val result = ArrayList<Blob>()
        val settled = ArrayList<Rect>()
        coordinates.keys.filter { it.startsWith("bubble:") }.forEach { key ->
            val id = key.removePrefix("bubble:")
            val bubble = rect(key) ?: return@forEach
            val full = Blob(bubble, radius)
            val g = growth(id)
            if (g < 1f && own) {
                // Your message comes up out of the composer, still tied to it by a bridge that
                // stretches and snaps before the capsule joins the column.
                val rise = dots * RISE_IN_DOTS
                val seed = Blob(bubble.translate(0f, rise).copy(left = bubble.left + bubble.width * 0.2f), radius)
                val moving = lerp(seed, full, g)
                result += moving
                if (g < SNAP_AT) {
                    val cx = moving.rect.center.x
                    val stretch = (1f - g / SNAP_AT)
                    result += Blob(Rect(cx - thread, moving.rect.bottom - radius, cx + thread, moving.rect.bottom + rise * stretch), thread)
                }
            } else if (g < 1f) {
                // A message that was being typed grows out of the typing dots.
                val dot = Rect(bubble.left, bubble.bottom - dots, bubble.left + dots * 2f, bubble.bottom)
                val seed = Blob(dot, dots / 2f)
                result += lerp(seed, full, g)
            } else {
                result += full
                settled += bubble
            }
            rect("quote:$id")?.let { quote ->
                result += Blob(quote, radius)
                val x = if (own) quote.right - radius else quote.left + radius
                result += Blob(Rect(x - thread / 2f, quote.bottom - radius / 2f, x + thread / 2f, bubble.top + radius / 2f), thread / 2f)
            }
            reactionBlobs(id, result)
        }
        // Messages of one author in a row are one column: each pair is tied by a wide neck, so
        // the column holds together whatever lies between them and however it is drawn.
        settled.sortedBy { it.top }.zipWithNext { upper, lower ->
            val left = maxOf(upper.left, lower.left) + radius * NECK_INSET
            val right = minOf(upper.right, lower.right) - radius * NECK_INSET
            if (right > left) result += Blob(Rect(left, upper.bottom - radius, right, lower.top + radius), radius / 2f)
        }
        coordinates.keys.filter { it.startsWith("circle:") }.forEach { key ->
            rect(key)?.let { result += Blob(it, minOf(it.width, it.height) / 2f) }
            reactionBlobs(key.removePrefix("circle:"), result)
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
    selected: Boolean = false,
    selecting: Boolean = false,
    flashing: Boolean = false,
) {
    val replyLabel = stringResource(R.string.chat_reply)
    val menuLabel = stringResource(R.string.chat_copy)
    val time = timeFormat.format(message.sentAt.atZone(ZoneId.systemDefault()))
    val status = if (own) stringResource(statusLabel(message.status)) else null
    val edited = if (message.edited) stringResource(R.string.chat_edited) else null
    val density = LocalDensity.current
    val swipeLimit = with(density) { ReplySwipe.toPx() }
    val drag = remember { Animatable(0f) }
    val haptics = rememberHaptics()
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
                    if (abs(drag.value) < swipeLimit && abs(next) >= swipeLimit) haptics.tick()
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
        val markup = remember(message.text) { Markup.parse(message.text) }
        val spoilerMask = stringResource(R.string.chat_spoiler)
        val revealLabel = stringResource(R.string.chat_spoiler_reveal)
        var revealed by remember(message.id) { mutableStateOf(false) }
        var expanded by remember(message.id) { mutableStateOf(false) }
        var cut by remember(message.id) { mutableStateOf(false) }
        val readMore = stringResource(R.string.chat_read_more)
        val hidden = !revealed && markup.spans.any { it.style == Markup.Style.Spoiler }
        val melting = message.expiresAt?.let { rememberRemaining(message.sentAt, it) }
        Box(
            modifier = Modifier
                .widthIn(max = 300.dp)
                // A one-line message is still a full touch target.
                .heightIn(min = Blesk.dimens.touchTarget)
                .onPlaced { parts.put(if (circle) "circle:${message.id}" else "bubble:${message.id}", it) }
                .let { if (selected) it.border(2.dp, Blesk.colors.accent, if (circle) CircleShape else BubbleShape) else it }
                .let { modifier ->
                    val shape = if (circle) CircleShape else BubbleShape
                    val glinting = melting?.let { left -> modifier.glint(shape, intensity = { left.value }) } ?: modifier
                    glinting.lightSweep(flashing, shape)
                }
                .combinedClickable(
                    onClickLabel = if (hidden && !selecting) revealLabel else null,
                    onLongClickLabel = menuLabel,
                    onLongClick = { actions.onMenu(message) },
                    onClick = { if (hidden && !selecting) revealed = true else actions.onTap(message) },
                    onDoubleClick = actions.onDoubleTap?.takeUnless { selecting }?.let { react -> { react(message) } },
                )
                .semantics(mergeDescendants = true) {
                    this.selected = selected
                    val body = if (hidden) markup.masked(spoilerMask) else markup.text
                    contentDescription = listOfNotNull(label.ifEmpty { null }, body.ifEmpty { null }, edited, time, status)
                        .joinToString(", ")
                    customActions = listOfNotNull(
                        CustomAccessibilityAction(replyLabel) { actions.onReply(message); true },
                        CustomAccessibilityAction(readMore) { expanded = true; true }.takeIf { cut && !expanded },
                    )
                }
                .graphicsLayer { alpha = contentAlpha() }
                // Opening a long message grows the bubble downwards in place, without a jump.
                .animateContentSize(Blesk.motion.settle())
                .padding(
                    horizontal = if (circle || attachment?.isVisual() == true) 4.dp else 14.dp,
                    vertical = if (circle || attachment?.isVisual() == true) 4.dp else 9.dp,
                ),
        ) {
            if (attachment is Attachment.Circle) {
                MediaContent(message, attachment)
                Stamp(time, message.status.takeIf { own }, edited, Modifier.align(Alignment.BottomEnd).padding(6.dp), onMedia = true)
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
                val stamp: @Composable () -> Unit = {
                    Stamp(
                        time, message.status.takeIf { own }, edited,
                        modifier = Modifier.padding(horizontal = if (attachment?.isVisual() == true) 8.dp else 0.dp),
                    )
                }
                if (message.text.isNotEmpty()) {
                    val inset = if (attachment?.isVisual() == true) Modifier.padding(horizontal = 10.dp, vertical = 6.dp) else Modifier
                    TextWithStamp(
                        modifier = inset,
                        text = { onLayout ->
                            FormattedText(
                                markup = markup,
                                revealed = revealed,
                                modifier = Modifier,
                                maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
                                onMention = actions.onMention,
                            ) { layout ->
                                if (!expanded) cut = layout.hasVisualOverflow
                                onLayout(layout)
                            }
                        },
                        stamp = stamp,
                    )
                    if (cut && !expanded) {
                        Text(
                            text = readMore,
                            style = Blesk.type.bodyStrong,
                            color = Blesk.colors.textSecondary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clearAndSetSemantics { }
                                .clickable { expanded = true },
                        )
                    }
                } else {
                    Box(Modifier.align(Alignment.End).padding(top = 2.dp)) { stamp() }
                }
            }
        }
        if (message.reactions.isNotEmpty()) {
            Reactions(
                reactions = message.reactions,
                onToggle = { actions.onReact(message, it) },
                onPlaced = { symbol, layout -> parts.put("reaction:${message.id}:$symbol", layout) },
                modifier = Modifier
                    .padding(horizontal = 10.dp)
                    .offset(y = (-6).dp),
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
private fun Stamp(
    time: String,
    status: SendStatus?,
    edited: String?,
    modifier: Modifier = Modifier,
    onMedia: Boolean = false,
) {
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
        if (status != null) StatusMark(status, color, onMedia)
    }
}

/**
 * Sent and delivered flash on the mark alone, since they happen with every message; read
 * lights the mark with the theme's light. The words are in the bubble's description.
 */
@Composable
private fun StatusMark(status: SendStatus, quiet: Color, onMedia: Boolean) {
    val colors = Blesk.colors
    val tint = when {
        onMedia -> quiet
        // Status marks sit on your own bubble, whose colour is the surface here.
        status == SendStatus.Failed -> remember(colors) { ensureContrast(colors.danger, colors.surface, ContrastLevel.UI_COMPONENT) }
        status == SendStatus.Read -> remember(colors) { ensureContrast(colors.light, colors.surface, ContrastLevel.UI_COMPONENT) }
        else -> quiet
    }
    val icon = when (status) {
        SendStatus.Failed -> BleskIcons.Alert
        SendStatus.Sending -> BleskIcons.Clock
        SendStatus.Sent -> BleskIcons.Check
        SendStatus.Delivered, SendStatus.Read -> BleskIcons.DoubleCheck
    }
    Icon(
        icon,
        contentDescription = null,
        size = 17.dp,
        tint = tint,
        modifier = Modifier.flashOn(status.takeIf { it == SendStatus.Sent || it == SendStatus.Delivered }, CircleShape),
    )
}

@Composable
private fun Reactions(
    reactions: ImmutableList<Reaction>,
    onToggle: (String) -> Unit,
    onPlaced: (symbol: String, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        reactions.forEach { reaction ->
            key(reaction.symbol) {
                val label = stringResource(R.string.chat_reaction, reaction.symbol, reaction.count)
                val ring by animateFloatAsState(if (reaction.mine) 1f else 0f, label = "mine")
                // A new reaction sticks on with a small ripple of the surface, then rests.
                val stick = remember { Animatable(STICK_FROM) }
                val motion = Blesk.motion
                LaunchedEffect(Unit) { stick.animateTo(1f, motion.stretch()) }
                Row(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = stick.value
                            scaleY = 2f - stick.value
                        }
                        .onPlaced { onPlaced(reaction.symbol, it) }
                        .border(1.dp, Blesk.colors.light.copy(alpha = 0.5f * ring), CircleShape)
                        .clickable { onToggle(reaction.symbol) }
                        .semantics(mergeDescendants = true) { contentDescription = label }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(reaction.symbol, style = Blesk.type.label)
                    if (reaction.count > 1) {
                        AnimatedContent(
                            targetState = reaction.count,
                            transitionSpec = {
                                val up = targetState > initialState
                                (slideInVertically(motion.settle()) { if (up) it else -it } + fadeIn(motion.press()))
                                    .togetherWith(slideOutVertically(motion.settle()) { if (up) -it else it } + fadeOut(motion.press()))
                            },
                            label = "count",
                        ) { count ->
                            Text(count.toString(), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
                        }
                    }
                }
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

/**
 * How much of a disappearing message's life is left, from 1 down to 0. The glint on its edge
 * fades with it instead of a countdown in numbers.
 */
@Composable
private fun rememberRemaining(sentAt: Instant, expiresAt: Instant): State<Float> {
    val total = (expiresAt.toEpochMilli() - sentAt.toEpochMilli()).coerceAtLeast(1)
    fun left() = ((expiresAt.toEpochMilli() - System.currentTimeMillis()).toFloat() / total).coerceIn(0f, 1f)
    return produceState(left(), sentAt, expiresAt) {
        // About two hundred steps over the whole life, never more often than once a second.
        val step = (total / MELT_STEPS).coerceAtLeast(MIN_MELT_STEP_MILLIS)
        while (value > 0f) {
            delay(step)
            value = left()
        }
    }
}

private const val MELT_STEPS = 200
private const val MIN_MELT_STEP_MILLIS = 1_000L

/** Formatted message text; spoilers stay covered until the bubble is tapped. */
/**
 * Message text with its markup. Mentions sit on small capsules and open the person; links are
 * in the theme's light and open in the browser.
 */
@Composable
private fun FormattedText(
    markup: Markup,
    revealed: Boolean,
    modifier: Modifier,
    maxLines: Int = Int.MAX_VALUE,
    onMention: ((String) -> Unit)? = null,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val colors = Blesk.colors
    val link = ensureContrast(colors.light, colors.surface, ContrastLevel.BODY_TEXT)
    val text = remember(markup, revealed, colors, onMention) {
        buildAnnotatedString {
            append(markup.text)
            mention.findAll(markup.text).forEach { match ->
                addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), match.range.first, match.range.last + 1)
                if (onMention != null) {
                    val username = match.value.removePrefix("@")
                    addLink(LinkAnnotation.Clickable(username) { onMention(username) }, match.range.first, match.range.last + 1)
                }
            }
            links(markup.text).forEach { range ->
                val url = markup.text.substring(range).let { if (it.startsWith("http")) it else "https://$it" }
                // Underlined as well: on a graphite bubble the light of a pale theme is close to the text colour.
                val style = SpanStyle(color = link, textDecoration = TextDecoration.Underline)
                addLink(LinkAnnotation.Url(url, TextLinkStyles(style)), range.first, range.last + 1)
            }
            markup.spans.forEach { span ->
                when (span.style) {
                    Markup.Style.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), span.start, span.end)
                    Markup.Style.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), span.start, span.end)
                    Markup.Style.Strike -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), span.start, span.end)
                    Markup.Style.Mono -> addStyle(
                        SpanStyle(fontFamily = FontFamily.Monospace, background = colors.hairline),
                        span.start,
                        span.end,
                    )
                    Markup.Style.Quote -> addStyle(SpanStyle(color = colors.textSecondary), span.start, span.end)
                    Markup.Style.Spoiler -> if (!revealed) {
                        addStyle(SpanStyle(color = Color.Transparent, background = colors.textTertiary), span.start, span.end)
                    }
                }
            }
        }
    }
    val mentions = remember(markup) { mention.findAll(markup.text).map { it.range }.toList() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val pill = colors.textPrimary.copy(alpha = MENTION_ALPHA)
    Text(
        text = text,
        modifier = modifier.drawBehind {
            val shown = layout ?: return@drawBehind
            val inset = 3.dp.toPx()
            mentions.forEach { range ->
                if (range.last >= shown.layoutInput.text.length) return@forEach
                val box = shown.getBoundingBox(range.first).let { first -> first.copy(right = shown.getBoundingBox(range.last).right) }
                drawRoundRect(
                    color = pill,
                    topLeft = Offset(box.left - inset, box.top),
                    size = Size(box.width + inset * 2, box.height),
                    cornerRadius = CornerRadius(box.height / 2f),
                )
            }
        },
        maxLines = maxLines,
        onTextLayout = { result ->
            layout = result
            onTextLayout?.invoke(result)
        },
    )
}

private val linkPattern = Regex("""(?i)\b(?:https?://|www\.)[^\s<>«»"]+""")

/** Web addresses in the text, without the punctuation a sentence puts after them. */
private fun links(text: String): List<IntRange> = linkPattern.findAll(text).map { match ->
    var end = match.range.last
    while (end > match.range.first && text[end] in TRAILING) end--
    match.range.first..end
}.toList()

private const val TRAILING = ".,;:!?)»"
private const val MENTION_ALPHA = 0.1f

/**
 * Text with the time and status tucked into the end of its last line when they fit there,
 * otherwise on a line of their own at the right, the way a short message stays one line tall.
 */
@Composable
private fun TextWithStamp(
    modifier: Modifier,
    text: @Composable (onLayout: (TextLayoutResult) -> Unit) -> Unit,
    stamp: @Composable () -> Unit,
) {
    val lastLine = remember { mutableFloatStateOf(0f) }
    val gap = with(LocalDensity.current) { StampGap.roundToPx() }
    Layout(
        content = {
            text { layout -> lastLine.floatValue = layout.getLineRight(layout.lineCount - 1) }
            stamp()
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val textPlaceable = measurables[0].measure(constraints.copy(minWidth = 0))
        val stampPlaceable = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        val inlineEnd = lastLine.floatValue.toInt() + gap + stampPlaceable.width
        val inline = inlineEnd <= constraints.maxWidth
        val width = if (inline) maxOf(textPlaceable.width, inlineEnd) else maxOf(textPlaceable.width, stampPlaceable.width)
        val height = if (inline) textPlaceable.height else textPlaceable.height + stampPlaceable.height
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            textPlaceable.place(0, 0)
            stampPlaceable.place(width - stampPlaceable.width, height - stampPlaceable.height)
        }
    }
}

private val StampGap = 8.dp

/** Longer messages fold, so one essay does not push the whole conversation off the screen. */
private const val COLLAPSED_LINES = 25

private val mention = Regex("(?<![\\w@])@[a-z0-9_]{3,32}")

private const val RISE_IN_DOTS = 3f
private const val SNAP_AT = 0.4f
private const val STICK_FROM = 0.8f
private val FailedGap = 14.dp
private const val NECK_INSET = 0.6f
