package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Connection
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.design.component.Avatar
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
import app.blesk.feature.chats.media.MediaController
import app.blesk.feature.chats.media.RecordMode
import app.blesk.feature.chats.media.Recorders
import app.blesk.feature.chats.media.RecordingUi
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.BotCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.ZoneId
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
    // The way down is offered once the newest message is more than a screen away.
    val farFromEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf false
            info.totalItemsCount - 1 - last.index > info.visibleItemsInfo.size
        }
    }
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
            // Behind the message menu the chat dims and blurs (blur draws from Android 12).
            val behindMenu by animateDpAsState(if (menuFor != null && !Blesk.motion.reduced) MENU_BLUR else 0.dp, label = "blur")
            LensScaffold(
                modifier = Modifier.blur(behindMenu),
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
                        showDown = farFromEnd,
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

private val MENU_BLUR = 10.dp
