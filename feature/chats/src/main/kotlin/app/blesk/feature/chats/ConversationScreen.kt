package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Connection
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.MAX_MESSAGE_CHARS
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.model.splitLongText
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.rememberCopy
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.motion.rememberLoopPhase
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.motion.sharedElement
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
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
import app.blesk.shared.protocol.BotCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
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
    /** Keeps the unsent text; called a moment after typing stops and when leaving. */
    val onDraft: (String) -> Unit = {},
    /** Opens the chat picker to forward a message; null where forwarding is not offered. */
    val onForward: ((messageIds: List<String>) -> Unit)? = null,
    /** Pins a message above the chat, or unpins with null. */
    val onPin: ((messageId: String?) -> Unit)? = null,
    /** A press on an inline button under a bot message. */
    val onButton: (messageId: String, button: BotButton) -> Unit = { _, _ -> },
    /** Tries the server again right away. */
    val onReconnect: () -> Unit = {},
    /** Sends a failed message once more. */
    val onRetry: (messageId: String) -> Unit = {},
    /** Remembers an emoji picked from the panel for its «recent» row. */
    val onEmojiPicked: (String) -> Unit = {},
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
    /** Public spaces and bots have no reactions. */
    reactions: Boolean = conversation.kind != ConversationKind.Bot,
    /** The other person's key changed and has not been checked since. */
    keyChanged: Boolean = false,
    /** The link to the server; while messages cannot leave they wait in the queue. */
    connection: Connection = Connection.Online,
    /** Enter sends the message; Shift+Enter breaks the line. */
    sendByEnter: Boolean = false,
    recentEmoji: ImmutableList<String> = persistentListOf(),
    /** What a bot offers when you type "/". */
    commands: ImmutableList<BotCommand> = persistentListOf(),
    /** Marks the chat as part of the demo world. */
    demo: Boolean = false,
    /** People who can be mentioned with "@" in a group or room. */
    members: ImmutableList<Contact> = persistentListOf(),
    /** Own username, to find messages that mention you; null when unknown. */
    me: String? = null,
    /** How many messages were unread when the chat was opened, before it was marked read. */
    unreadAtOpen: Int = conversation.unread,
    underlay: @Composable BoxScope.() -> Unit = {},
) {
    val recording = remember { RecordingUi() }
    val haptics = rememberHaptics()
    val listState = rememberLazyListState()
    val initialIds = remember(conversation.id) { messages.map { it.id }.toSet() }
    var replyToId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable(conversation.id) { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<Message?>(null) }
    var selection by remember(conversation.id) { mutableStateOf<Set<String>>(emptySet()) }
    BackHandler(enabled = selection.isNotEmpty()) { selection = emptySet() }
    val input = rememberTextFieldState(conversation.draft)
    DraftKeeper(input, callbacks.onDraft)
    val notices = LocalNotices.current
    val resources = LocalContext.current.resources

    // Deleting waits out «Отменить»; until then the screen that owns the list hides the messages.
    fun deleteWithUndo(ids: List<String>) {
        val text = resources.getQuantityString(R.plurals.chat_deleted, ids.size, ids.size)
        if (notices == null) ids.forEach(callbacks.onDelete) else notices.undoable(text, hidden = ids) { ids.forEach(callbacks.onDelete) }
    }
    val byId = remember(messages) { messages.associateBy { it.id } }
    // The divider stays where the chat was left, even after the messages are marked read.
    val unreadFrom = remember(conversation.id) {
        unreadAtOpen.takeIf { it > 0 }?.let { messages.getOrNull(messages.size - it) }?.takeUnless { it.isOwn }?.id
    }
    val feed = remember(messages, unreadFrom) { buildFeed(messages, ZoneId.systemDefault(), unreadFrom) }
    var mentions by remember(conversation.id) {
        val tag = me?.let { "@$it" }
        val unread = unreadFrom?.let { from -> messages.dropWhile { it.id != from } }.orEmpty()
        mutableStateOf(if (tag == null) emptyList() else unread.filter { it.text.contains(tag, ignoreCase = true) }.map { it.id })
    }
    val showAuthors = conversation.kind == ConversationKind.Group || conversation.kind == ConversationKind.Room
    val you = stringResource(R.string.chat_you)

    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            // One item of slack: a message that has just arrived must not count as scrolled away.
            last == null || last.index >= info.totalItemsCount - 2
        }
    }
    val pinned = conversation.pinnedMessageId?.let(byId::get)
    // The protection note, and possibly the pinned banner and key warning, come before the feed.
    val leading = 1 + (if (pinned != null) 1 else 0) + (if (keyChanged) 1 else 0)
    var seen by remember(conversation.id) { mutableIntStateOf(messages.size) }
    var opened by remember(conversation.id) { mutableStateOf(false) }
    LaunchedEffect(messages.size, typing) {
        // Counted from the data, not the layout: on the first frame the list is not measured yet.
        val total = leading + feed.size + if (typing) 1 else 0
        val divider = feed.indexOf(FeedEntry.Unread)
        when {
            !opened && divider >= 0 -> listState.scrollToItem(divider + leading)
            !opened -> listState.scrollToItem(total - 1)
            atBottom || messages.lastOrNull()?.isOwn == true -> {
                listState.animateScrollToItem(total - 1)
                seen = messages.size
            }
        }
        opened = true
    }
    LaunchedEffect(atBottom) { if (atBottom) seen = messages.size }
    val lastOwnStatus = messages.lastOrNull { it.isOwn }?.status
    // Sending retries quietly; only the final failure is felt, once.
    LaunchedEffect(lastOwnStatus) {
        if (lastOwnStatus == SendStatus.Failed) haptics.refuse()
    }

    val actions = remember(conversation.id) {
        val react: (Message, String) -> Unit = { message, symbol ->
            haptics.stick()
            callbacks.onReact(message.id, symbol)
        }
        MessageActions(
            onReply = { replyToId = it.id; editingId = null },
            onMenu = {
                haptics.menu()
                menuFor = it
            },
            onReact = react,
            onApplyTheme = callbacks.onApplyTheme,
            onButton = { message, button -> callbacks.onButton(message.id, button) },
            onDoubleTap = if (reactions) ({ message -> react(message, QuickReactions.first()) }) else null,
            onTap = { message ->
                when {
                    selection.isNotEmpty() -> selection = if (message.id in selection) selection - message.id else selection + message.id
                    // A failed message answers a tap with what can be done about it.
                    message.isOwn && message.status == SendStatus.Failed -> menuFor = message
                }
            },
        )
    }

    CompositionLocalProvider(LocalMediaController provides media) {
        Box(modifier.sharedBounds(chatKey(conversation.id)).fillMaxSize()) {
            LensScaffold(
                underlay = underlay,
                top = {
                    if (selection.isNotEmpty()) {
                        val picked = messages.filter { it.id in selection }
                        val copy = rememberCopy()
                        SelectionHeader(
                            count = picked.size,
                            onClose = { selection = emptySet() },
                            onCopy = {
                                val text = picked.joinToString("\n") { it.text }
                                copy(text, false)
                                selection = emptySet()
                            },
                            onForward = callbacks.onForward?.let { forward ->
                                {
                                    forward(picked.map { it.id })
                                    selection = emptySet()
                                }
                            },
                            onDelete = if (picked.all { it.isOwn } && conversation.kind != ConversationKind.Bot) {
                                {
                                    deleteWithUndo(picked.map { it.id })
                                    selection = emptySet()
                                }
                            } else {
                                null
                            },
                        )
                    } else {
                        Column {
                            ChatHeader(
                                conversation, typing, callbacks.onBack, callbacks.onOpenChatTheme, callbacks.onOpenInfo,
                                callbacks.onCall, demo,
                            )
                            ConnectionStatus(connection, callbacks.onReconnect)
                        }
                    }
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
                            attachments = conversation.kind != ConversationKind.Bot,
                            commands = commands,
                            members = members,
                            online = connection.canSend,
                            sendByEnter = sendByEnter,
                            recentEmoji = recentEmoji,
                            onEmojiPicked = callbacks.onEmojiPicked,
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
                                    haptics.detach()
                                }
                                replyToId = null
                                editingId = null
                            },
                        )
                    }
                },
            ) { padding ->
                // The composer rises with the keyboard frame by frame; the newest messages rise with it.
                val bottomPx = with(LocalDensity.current) { padding.calculateBottomPadding().toPx() }
                val lastBottom = remember { floatArrayOf(bottomPx) }
                val following = atBottom
                SideEffect {
                    val delta = bottomPx - lastBottom[0]
                    lastBottom[0] = bottomPx
                    if (delta > 0f && following) listState.dispatchRawDelta(delta)
                }
                Box(Modifier.fillMaxSize()) {
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
                        pinned?.let { message ->
                            item(key = "pinned") { PinnedBanner(message, onUnpin = callbacks.onPin?.let { unpin -> { unpin(null) } }) }
                        }
                        if (keyChanged) item(key = "key") { KeyWarning(callbacks.onOpenInfo) }
                        if (messages.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    text = stringResource(R.string.chat_empty),
                                    color = Blesk.colors.textSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                                )
                            }
                        }
                        items(feed, key = { it.key }) { entry ->
                            val group = when (entry) {
                                is FeedEntry.Day -> return@items DayDivider(entry.date)
                                FeedEntry.Unread -> return@items UnreadDivider()
                                is FeedEntry.Run -> entry.messages
                            }
                            val own = group.first().isOwn
                            // A deleted message fades out and its neighbours close the gap without a jump.
                            val itemMotion = if (Blesk.motion.reduced) {
                                Modifier
                            } else {
                                Modifier.animateItem(
                                    fadeInSpec = null,
                                    placementSpec = Blesk.motion.settle(),
                                    fadeOutSpec = Blesk.motion.transition(),
                                )
                            }
                            MessageColumn(
                                modifier = itemMotion,
                                messages = group,
                                author = if (showAuthors && !own) authorName(group.first().authorId) else null,
                                fresh = remember(group) { group.map { it.id }.filterNot { it in initialIds }.toSet() },
                                quoteText = { id -> byId[id]?.let { quoted -> quoteOf(quoted, you, authorName) } },
                                actions = actions,
                                selection = selection,
                            )
                        }
                        if (typing) item(key = "typing") { TypingDots() }
                    }
                    val scope = rememberCoroutineScope()
                    JumpButtons(
                        showDown = !atBottom,
                        unseen = (messages.size - seen).coerceAtLeast(0),
                        mentions = mentions.size,
                        onDown = { scope.launch { listState.animateScrollToItem(leading + feed.size) } },
                        onMention = {
                            val target = mentions.first()
                            mentions = mentions.drop(1)
                            val index = feed.indexOfFirst { it is FeedEntry.Run && it.messages.any { message -> message.id == target } }
                            if (index >= 0) scope.launch { listState.animateScrollToItem(index + leading) }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = Blesk.dimens.gutter, bottom = padding.calculateBottomPadding() + 12.dp),
                    )
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
                    onDelete = { deleteWithUndo(listOf(message.id)) },
                    editable = conversation.kind != ConversationKind.Bot,
                    onForward = callbacks.onForward?.let { forward -> { forward(listOf(message.id)) } },
                    onSelect = { selection = setOf(message.id) },
                    onRetry = if (message.isOwn && message.status == SendStatus.Failed) ({ callbacks.onRetry(message.id) }) else null,
                    onPin = callbacks.onPin?.let { pin -> { pin(if (conversation.pinnedMessageId == message.id) null else message.id) } },
                    pinned = conversation.pinnedMessageId == message.id,
                    onReact = if (reactions) ({ symbol -> actions.onReact(message, symbol) }) else null,
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

@Composable
private fun ChatHeader(
    conversation: Conversation,
    typing: Boolean,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenInfo: (() -> Unit)?,
    onCall: ((Boolean) -> Unit)?,
    demo: Boolean,
) {
    val protected = conversation.protection == Protection.EndToEnd
    TopLens(
        title = null,
        onBack = onBack,
        leading = {
            Avatar(
                name = conversation.title,
                key = conversation.id,
                size = 40.dp,
                modifier = Modifier.sharedElement(avatarKey(conversation.id)),
                protected = protected && conversation.kind != ConversationKind.Channel && conversation.kind != ConversationKind.Bot,
            )
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                Text(conversation.title, style = Blesk.type.bodyStrong, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (demo) DemoTag()
                    if (!typing && conversation.timerSeconds > 0) {
                        Icon(BleskIcons.Timer, null, tint = Blesk.colors.textSecondary, size = 14.dp)
                    } else if (!typing && protected) {
                        Icon(BleskIcons.Shield, null, tint = Blesk.colors.textSecondary, size = 14.dp)
                    }
                    Text(
                        text = when {
                            typing -> stringResource(R.string.chats_typing)
                            conversation.timerSeconds > 0 -> stringResource(R.string.chat_timer, timerLabel(conversation.timerSeconds))
                            protected -> stringResource(R.string.chat_protected)
                            conversation.kind == ConversationKind.Bot -> stringResource(R.string.chat_bot_label)
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
            if (onOpenInfo != null) {
                if (conversation.kind == ConversationKind.Direct) {
                    IconButton(BleskIcons.Info, stringResource(R.string.info_title), onOpenInfo)
                } else {
                    IconButton(BleskIcons.People, stringResource(R.string.group_info), onOpenInfo)
                }
            }
            // Direct chats have the theme in their info screen; the header is full with calls there.
            if (conversation.kind != ConversationKind.Direct || onOpenInfo == null) {
                IconButton(BleskIcons.Appearance, stringResource(R.string.chat_theme), onOpenTheme)
            }
        },
    )
}

@Composable
private fun DayDivider(date: LocalDate) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> date.format(if (date.year == today.year) dayFormat else dayYearFormat)
    }
    Text(
        text = text,
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { heading() },
    )
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM")
private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Composable
private fun UnreadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Blesk.colors.hairline))
        Text(stringResource(R.string.chat_unread_divider), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(Blesk.colors.hairline))
    }
}

/** Round matte buttons over the messages: to the first unread mention of you, and to the bottom. */
@Composable
private fun JumpButtons(
    showDown: Boolean,
    unseen: Int,
    mentions: Int,
    onDown: () -> Unit,
    onMention: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        if (mentions > 0) {
            JumpButton(BleskIcons.At, pluralStringResource(R.plurals.chat_jump_mentions, mentions, mentions), mentions, onMention)
        }
        if (showDown) {
            val label = if (unseen > 0) {
                pluralStringResource(R.plurals.chat_jump_new, unseen, unseen)
            } else {
                stringResource(R.string.chat_jump_down)
            }
            JumpButton(BleskIcons.ArrowDown, label, unseen, onDown)
        }
    }
}

@Composable
private fun JumpButton(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Blesk.colors.surfaceStrong)
                .border(1.dp, Blesk.colors.hairline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Blesk.colors.textPrimary)
        }
        if (count > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clearAndSetSemantics { }) {
                UnreadBadge(count)
            }
        }
    }
}

/** Replaces the chat header while messages are picked. */
@Composable
private fun SelectionHeader(count: Int, onClose: () -> Unit, onCopy: () -> Unit, onForward: (() -> Unit)?, onDelete: (() -> Unit)?) {
    TopLens(
        title = pluralStringResource(R.plurals.chat_selected, count, count),
        leading = { IconButton(BleskIcons.Close, stringResource(R.string.chat_cancel), onClose) },
        actions = {
            IconButton(BleskIcons.Copy, stringResource(R.string.chat_copy), onCopy)
            onForward?.let { IconButton(BleskIcons.Forward, stringResource(R.string.chat_forward), it) }
            onDelete?.let { IconButton(BleskIcons.Trash, stringResource(R.string.chat_delete), it) }
        },
    )
}

@Composable
private fun KeyWarning(onCheck: (() -> Unit)?) {
    Capsule(
        modifier = Modifier.fillMaxWidth(),
        onClick = onCheck,
        onClickLabel = stringResource(R.string.chat_verify),
    ) {
        Text(
            text = stringResource(R.string.chat_key_changed),
            style = Blesk.type.label,
            color = Blesk.colors.danger,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
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
    attachments: Boolean,
    commands: ImmutableList<BotCommand>,
    members: ImmutableList<Contact>,
    online: Boolean,
    sendByEnter: Boolean,
    recentEmoji: ImmutableList<String>,
    onEmojiPicked: (String) -> Unit,
    replyTo: Message?,
    editing: Message?,
    authorName: (Message) -> String,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
) {
    val hint = stringResource(R.string.chat_input_hint)
    var attachMenu by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = emojiOpen) { emojiOpen = false }
    fun send() {
        val text = state.text.toString()
        if (text.isNotBlank()) {
            onSend(text)
            state.clearText()
        }
    }
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
            if (!online) {
                Text(
                    text = stringResource(R.string.chat_offline),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val typed = state.text.toString()
            if (typed.length > MAX_MESSAGE_CHARS) {
                val parts = splitLongText(typed).size
                Text(
                    text = pluralStringResource(R.plurals.chat_long_text, parts, parts),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val suggestions = if (typed.startsWith("/") && ' ' !in typed) {
                commands.filter { it.command.startsWith(typed.removePrefix("/")) }.take(MAX_SUGGESTIONS)
            } else {
                emptyList()
            }
            suggestions.forEach { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { state.setTextAndPlaceCursorAtEnd("/${command.command} ") }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("/${command.command}", style = Blesk.type.bodyStrong)
                    Text(command.description, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                }
            }
            val word = typed.substringAfterLast(' ').substringAfterLast('\n')
            if (word.startsWith("@") && members.isNotEmpty()) {
                val needle = word.removePrefix("@").lowercase()
                members.filter { it.username.startsWith(needle) || it.name.lowercase().startsWith(needle) }
                    .take(MAX_SUGGESTIONS)
                    .forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    state.setTextAndPlaceCursorAtEnd(typed.dropLast(word.length) + "@${person.username} ")
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(person.name, size = 28.dp)
                            Text(person.name, style = Blesk.type.bodyStrong, maxLines = 1)
                            Text("@${person.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                        }
                    }
            }
            if (recording.active && recorders != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordingBar(recording, recorders, Modifier.weight(1f))
                    if (!recording.locked) RecordButton(recording, recorders)
                }
                return@BottomLens
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (attachments) IconButton(BleskIcons.Paperclip, stringResource(R.string.media_attach), { attachMenu = !attachMenu })
                Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary)
                    BasicTextField(
                        state = state,
                        textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                        cursorBrush = SolidColor(Blesk.colors.accent),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                        keyboardOptions = KeyboardOptions(imeAction = if (sendByEnter) ImeAction.Send else ImeAction.Default),
                        onKeyboardAction = if (sendByEnter) KeyboardActionHandler { send() } else null,
                        // The padding is inside the field, so the whole 48dp row takes the tap.
                        decorator = { inner ->
                            Box(
                                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { inner() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) emojiOpen = false }
                            .onPreviewKeyEvent { event ->
                                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                                if (!sendByEnter || !enter || event.isShiftPressed) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) send()
                                true
                            }
                            .semantics { contentDescription = hint },
                    )
                }
                IconButton(
                    icon = if (emojiOpen) BleskIcons.Edit else BleskIcons.Smile,
                    contentDescription = stringResource(if (emojiOpen) R.string.emoji_keyboard else R.string.emoji_open),
                    onClick = {
                        if (emojiOpen) {
                            emojiOpen = false
                        } else {
                            attachMenu = false
                            keyboard?.hide()
                            focus.clearFocus()
                            emojiOpen = true
                        }
                    },
                )
                if (state.text.isBlank() && editing == null && recorders != null) {
                    RecordButton(recording, recorders)
                } else {
                    IconButton(
                        icon = if (editing != null) BleskIcons.Check else BleskIcons.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        onClick = ::send,
                    )
                }
            }
            EmojiRoom(open = emojiOpen) { height ->
                EmojiPanel(
                    recent = recentEmoji,
                    onPick = { emoji ->
                        state.edit { replace(selection.min, selection.max, emoji) }
                        onEmojiPicked(emoji)
                    },
                    height = height,
                )
            }
        }
        if (recording.active && !recording.locked) {
            LockDrop(recording, Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (-150).dp))
        }
    }
}

/** Saves what is typed a moment after typing stops, and once more when the chat closes. */
@Composable
private fun DraftKeeper(state: TextFieldState, onDraft: (String) -> Unit) {
    val save by rememberUpdatedState(onDraft)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collectLatest { text ->
                delay(DRAFT_PAUSE_MILLIS)
                save(text)
            }
    }
    DisposableEffect(state) { onDispose { save(state.text.toString()) } }
}

@Composable
private fun PinnedBanner(message: Message, onUnpin: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Blesk.dimens.capsuleRadius))
            .background(Blesk.colors.surface)
            .padding(start = 14.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(BleskIcons.Pin, contentDescription = stringResource(R.string.chat_pinned), size = 18.dp, tint = Blesk.colors.textSecondary)
        Text(
            text = message.text.ifEmpty { stringResource(R.string.chat_pinned) },
            style = Blesk.type.label,
            maxLines = 2,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp),
        )
        if (onUnpin != null) IconButton(BleskIcons.Close, stringResource(R.string.chat_unpin), onUnpin)
    }
}

/**
 * Room for the emoji panel exactly as tall as the last keyboard. While the keyboard slides away
 * the room grows by as much as the keyboard shrinks, and the other way round, so the composer
 * and the messages never jump when switching between the two. After the panel closes for the
 * keyboard the room is held until the keyboard is all the way up.
 */
@Composable
private fun EmojiRoom(open: Boolean, panel: @Composable (Dp) -> Unit) {
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val navigation = WindowInsets.navigationBars.getBottom(density)
    var keyboard by rememberSaveable { mutableIntStateOf(0) }
    if (ime > keyboard) keyboard = ime
    val full = if (keyboard > 0) keyboard else with(density) { DefaultPanel.roundToPx() + navigation }
    var holding by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(open) }
    if (wasOpen && !open) holding = true
    wasOpen = open
    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        delay(HOLD_MAX_MILLIS)
        holding = false
    }
    if (holding && ime >= full - with(density) { 8.dp.roundToPx() }) holding = false
    val room = if (open || holding) (full - maxOf(ime, navigation)).coerceAtLeast(0) else 0
    if (room == 0 && !open) return
    val panelHeight = with(density) { (full - navigation).toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { room.toDp() })
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (open) Box(Modifier.requiredHeight(panelHeight)) { panel(panelHeight) }
    }
}

private val DefaultPanel = 300.dp
private const val HOLD_MAX_MILLIS = 700L

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

private const val MAX_SUGGESTIONS = 5
private const val DRAFT_PAUSE_MILLIS = 800L
