package app.blesk.feature.chats

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.haptics.rememberHaptics
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.media.CircleOverlay
import app.blesk.feature.chats.media.LocalMediaController
import app.blesk.feature.chats.media.LockDrop
import app.blesk.feature.chats.media.MediaController
import app.blesk.feature.chats.media.RecordButton
import app.blesk.feature.chats.media.RecordMode
import app.blesk.feature.chats.media.Recorders
import app.blesk.feature.chats.media.RecordingBar
import app.blesk.feature.chats.media.RecordingUi
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlin.math.PI
import kotlin.math.sin

/** What the conversation screen can ask the data layer to do. */
class ConversationCallbacks(
    val onSend: (text: String, replyToId: String?) -> Unit,
    val onEdit: (messageId: String, text: String) -> Unit,
    val onDelete: (messageId: String) -> Unit,
    val onReact: (messageId: String, symbol: String) -> Unit,
    val onApplyTheme: (ThemeSpec) -> Unit,
    val onOpenChatTheme: () -> Unit,
    val onPickMedia: () -> Unit,
    val onPickFile: () -> Unit,
    val onBack: () -> Unit,
    /** Whether you are composing; the screen calls it with a pause after the last keystroke. */
    val onTyping: (Boolean) -> Unit = {},
    /** Opens members, roles and settings of a group or room; null for direct chats. */
    val onOpenInfo: (() -> Unit)? = null,
    /** Calls the person; null where calls make no sense (groups, channels, bots). */
    val onCall: ((video: Boolean) -> Unit)? = null,
)

@Composable
fun ConversationScreen(
    conversation: Conversation,
    messages: ImmutableList<Message>,
    typing: Boolean,
    authorName: (String) -> String?,
    callbacks: ConversationCallbacks,
    modifier: Modifier = Modifier,
    recorders: Recorders? = null,
    media: MediaController = MediaController.Inert,
    busy: Boolean = false,
    /** Nobody but admins may write here; the composer is replaced with a note. */
    readOnly: Boolean = conversation.kind == ConversationKind.Channel,
    /** Public spaces have no reactions. */
    reactions: Boolean = true,
    underlay: @Composable BoxScope.() -> Unit = {},
) {
    val recording = remember { RecordingUi() }
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val initialIds = remember(conversation.id) { messages.map { it.id }.toSet() }
    var replyToId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Message?>(null) }
    val input = rememberTextFieldState()
    val byId = remember(messages) { messages.associateBy { it.id } }
    val groups = remember(messages) { groupMessages(messages) }
    val showAuthors = conversation.kind == ConversationKind.Group || conversation.kind == ConversationKind.Room
    val you = stringResource(R.string.chat_you)

    LaunchedEffect(messages.size, typing) {
        val last = groups.size + if (typing) 1 else 0
        if (last > 0) listState.animateScrollToItem(last)
    }
    val lastOwnStatus = messages.lastOrNull { it.isOwn }?.status
    LaunchedEffect(lastOwnStatus) {
        if (lastOwnStatus == SendStatus.Delivered) haptics.event()
    }

    val actions = remember(conversation.id) {
        MessageActions(
            onReply = { replyToId = it.id; editingId = null },
            onMenu = { menuFor = it },
            onReact = { message, symbol -> callbacks.onReact(message.id, symbol) },
            onApplyTheme = callbacks.onApplyTheme,
        )
    }

    CompositionLocalProvider(LocalMediaController provides media) {
        Box(modifier.fillMaxSize()) {
            LensScaffold(
                underlay = underlay,
                top = {
                    ChatHeader(conversation, typing, callbacks.onBack, callbacks.onOpenChatTheme, callbacks.onOpenInfo, callbacks.onCall)
                },
                bottom = {
                    if (readOnly) {
                        BottomLens {
                            Text(
                                text = if (conversation.kind == ConversationKind.Channel) {
                                    stringResource(R.string.chat_channel_readonly)
                                } else {
                                    stringResource(R.string.group_readonly)
                                },
                                style = Blesk.type.label,
                                color = Blesk.colors.textSecondary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                            )
                        }
                    } else {
                        TypingSignal(input, callbacks.onTyping)
                        Composer(
                            state = input,
                            recording = recording,
                            recorders = recorders,
                            busy = busy,
                            onPickMedia = callbacks.onPickMedia,
                            onPickFile = callbacks.onPickFile,
                            replyTo = replyToId?.let(byId::get),
                            editing = editingId?.let(byId::get),
                            authorName = { if (it.isOwn) you else authorName(it.authorId).orEmpty() },
                            onCancel = {
                                if (editingId != null) input.clearText()
                                replyToId = null
                                editingId = null
                            },
                            onSend = { text ->
                                val editing = editingId
                                if (editing != null) {
                                    callbacks.onEdit(editing, text)
                                } else {
                                    callbacks.onSend(text, replyToId)
                                    haptics.merge()
                                }
                                replyToId = null
                                editingId = null
                            },
                        )
                    }
                },
            ) { padding ->
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = Blesk.dimens.gutter,
                        end = Blesk.dimens.gutter,
                        top = padding.calculateTopPadding() + 8.dp,
                        bottom = padding.calculateBottomPadding() + 12.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item(key = "note") { ProtectionNote(conversation.protection) }
                    items(groups, key = { it.first().id }) { group ->
                        val own = group.first().isOwn
                        MessageColumn(
                            messages = group,
                            author = if (showAuthors && !own) authorName(group.first().authorId) else null,
                            fresh = remember(group) { group.map { it.id }.filterNot { it in initialIds || own }.toSet() },
                            quoteText = { id -> byId[id]?.let { quoted -> quoteOf(quoted, you, authorName) } },
                            actions = actions,
                        )
                    }
                    if (typing) item(key = "typing") { TypingDots() }
                }
            }
            if (recording.active && recording.mode == RecordMode.Circle && recorders != null) {
                CircleOverlay(recording, recorders)
            }
            menuFor?.let { message ->
                MessageMenu(
                    message = message,
                    onDismiss = { menuFor = null },
                    onReply = { replyToId = message.id; editingId = null },
                    onEdit = {
                        editingId = message.id
                        replyToId = null
                        input.setTextAndPlaceCursorAtEnd(message.text)
                    },
                    onDelete = { callbacks.onDelete(message.id) },
                    onReact = if (reactions) ({ symbol -> callbacks.onReact(message.id, symbol) }) else null,
                )
            }
        }
    }
}

private fun quoteOf(message: Message, you: String, authorName: (String) -> String?): String {
    val who = if (message.isOwn) you else authorName(message.authorId)?.substringBefore(' ').orEmpty()
    return "$who: ${message.text}"
}

/** Runs of one author's messages, broken after a quiet gap. */
private fun groupMessages(messages: List<Message>): List<ImmutableList<Message>> {
    val groups = ArrayList<ImmutableList<Message>>()
    var current = ArrayList<Message>()
    messages.forEach { message ->
        val previous = current.lastOrNull()
        val sameRun = previous != null &&
            previous.authorId == message.authorId &&
            message.sentAt.epochSecond - previous.sentAt.epochSecond < GROUP_GAP_SECONDS
        if (!sameRun && current.isNotEmpty()) {
            groups += current.toImmutableList()
            current = ArrayList()
        }
        current += message
    }
    if (current.isNotEmpty()) groups += current.toImmutableList()
    return groups
}

private const val GROUP_GAP_SECONDS = 10 * 60

@Composable
private fun ChatHeader(
    conversation: Conversation,
    typing: Boolean,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenInfo: (() -> Unit)?,
    onCall: ((Boolean) -> Unit)?,
) {
    val protected = conversation.protection == Protection.EndToEnd
    TopLens(
        title = null,
        onBack = onBack,
        leading = {
            Avatar(conversation.title, size = 40.dp)
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(conversation.title, style = Blesk.type.bodyStrong, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (!typing && protected) Icon(BleskIcons.Shield, null, tint = Blesk.colors.textSecondary, size = 14.dp)
                    Text(
                        text = when {
                            typing -> stringResource(R.string.chats_typing)
                            protected -> stringResource(R.string.chat_protected)
                            else -> stringResource(app.blesk.core.design.R.string.design_not_encrypted)
                        },
                        style = Blesk.type.caption,
                        color = if (typing) Blesk.colors.accent else Blesk.colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        actions = {
            if (onCall != null) {
                IconButton(BleskIcons.Phone, stringResource(R.string.chat_call), { onCall(false) })
                IconButton(BleskIcons.Video, stringResource(R.string.chat_video_call), { onCall(true) })
            }
            if (onOpenInfo != null) IconButton(BleskIcons.People, stringResource(R.string.group_info), onOpenInfo)
            IconButton(BleskIcons.Appearance, stringResource(R.string.chat_theme), onOpenTheme)
        },
    )
}

@Composable
private fun ProtectionNote(protection: Protection) {
    Text(
        text = stringResource(if (protection == Protection.EndToEnd) R.string.chat_encrypted_note else R.string.chat_open_note),
        style = Blesk.type.caption,
        color = Blesk.colors.textTertiary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

/** Three dots bobbing in a capsule; the incoming message later grows out of the same spot. */
@Composable
private fun TypingDots() {
    val phase by rememberLoopPhase(1100)
    val reduced = Blesk.motion.reduced
    val label = stringResource(R.string.chats_typing)
    val dot = Blesk.colors.textTertiary
    Box(
        Modifier
            .size(width = 64.dp, height = 36.dp)
            .semantics { contentDescription = label }
            .metaballs(Blesk.colors.surface, smoothness = 10.dp) {
                listOf(Blob(Rect(0f, 0f, size.width, size.height), size.height / 2f))
            }
            .drawBehind {
                repeat(3) { i ->
                    val wave = if (reduced) 0f else sin((phase - i * 0.18f) * 2f * PI).toFloat()
                    drawCircle(
                        color = dot,
                        radius = 3.5.dp.toPx(),
                        center = Offset(size.width * (0.3f + 0.2f * i), size.height / 2f - wave * 3.dp.toPx()),
                    )
                }
            },
    )
}

@Composable
private fun Composer(
    state: TextFieldState,
    recording: RecordingUi,
    recorders: Recorders?,
    busy: Boolean,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    replyTo: Message?,
    editing: Message?,
    authorName: (Message) -> String,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
) {
    val hint = stringResource(R.string.chat_input_hint)
    var attachMenu by remember { mutableStateOf(false) }
    Box {
        BottomLens {
            AnimatedVisibility(visible = replyTo != null || editing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            editing != null -> stringResource(R.string.chat_editing)
                            replyTo != null -> stringResource(R.string.chat_replying_to, "${authorName(replyTo)}: ${replyTo.text}")
                            else -> ""
                        },
                        style = Blesk.type.label,
                        color = Blesk.colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(BleskIcons.Close, stringResource(R.string.chat_cancel), onCancel)
                }
            }
            AnimatedVisibility(visible = attachMenu && !recording.active) {
                Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        text = stringResource(R.string.media_photo_video),
                        icon = BleskIcons.Image,
                        kind = ButtonKind.Secondary,
                        onClick = {
                            attachMenu = false
                            onPickMedia()
                        },
                    )
                    Button(
                        text = stringResource(R.string.media_file),
                        icon = BleskIcons.Document,
                        kind = ButtonKind.Secondary,
                        onClick = {
                            attachMenu = false
                            onPickFile()
                        },
                    )
                }
            }
            if (busy) {
                Text(
                    text = stringResource(R.string.media_preparing),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            if (recording.active && recorders != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordingBar(recording, recorders, Modifier.weight(1f))
                    if (!recording.locked) RecordButton(recording, recorders)
                }
                return@BottomLens
            }
            Row(verticalAlignment = Alignment.Bottom) {
                IconButton(BleskIcons.Paperclip, stringResource(R.string.media_attach), { attachMenu = !attachMenu })
                Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary)
                    BasicTextField(
                        state = state,
                        textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                        cursorBrush = SolidColor(Blesk.colors.accent),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .semantics { contentDescription = hint },
                    )
                }
                if (state.text.isBlank() && editing == null && recorders != null) {
                    RecordButton(recording, recorders)
                } else {
                    IconButton(
                        icon = if (editing != null) BleskIcons.Check else BleskIcons.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        onClick = {
                            val text = state.text.toString()
                            if (text.isNotBlank()) {
                                onSend(text)
                                state.clearText()
                            }
                        },
                    )
                }
            }
        }
        if (recording.active && !recording.locked) {
            LockDrop(recording, Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (-150).dp))
        }
    }
}

/** Sends "typing" at most every few seconds while text changes, and "stopped" after a pause. */
@Composable
private fun TypingSignal(state: TextFieldState, onTyping: (Boolean) -> Unit) {
    LaunchedEffect(state) {
        var lastSent = 0L
        var active = false
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collectLatest { text ->
                val now = System.currentTimeMillis()
                if (text.isNotBlank() && (!active || now - lastSent > TYPING_REPEAT_MILLIS)) {
                    onTyping(true)
                    active = true
                    lastSent = now
                }
                delay(TYPING_PAUSE_MILLIS)
                if (active) {
                    onTyping(false)
                    active = false
                }
            }
    }
}

private const val TYPING_REPEAT_MILLIS = 4_000L
private const val TYPING_PAUSE_MILLIS = 5_000L
