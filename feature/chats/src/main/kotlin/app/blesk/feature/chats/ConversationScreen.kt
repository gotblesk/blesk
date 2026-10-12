package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Connection
import app.blesk.core.data.model.BotButton
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Presence
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.GestureHint
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Capsule
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.component.GroupAvatar
import app.blesk.core.design.component.HintCapsule
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.rememberCopy
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.glass.lens
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
import app.blesk.feature.chats.media.LocalVeiledMedia
import app.blesk.feature.chats.media.MediaController
import app.blesk.feature.chats.media.RecordMode
import app.blesk.feature.chats.media.Recorders
import app.blesk.feature.chats.media.RecordingUi
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.protocol.BotCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.Instant
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
    /** Pins a message above the chat, or unpins it. */
    val onPin: ((messageId: String, pinned: Boolean) -> Unit)? = null,
    /** A press on an inline button under a bot message. */
    val onButton: (messageId: String, button: BotButton) -> Unit = { _, _ -> },
    /** Tries the server again right away. */
    val onReconnect: () -> Unit = {},
    /** Unblocks the other person of a direct chat. */
    val onUnblock: (() -> Unit)? = null,
    /** For a message request: accept, delete or block instead of answering. */
    val request: RequestActions? = null,
    /** Opens the profile of an @username mentioned in a message; null where nobody can be opened. */
    val onOpenPerson: ((username: String) -> Unit)? = null,
    /** Opens the camera for one photo; null where it is not offered. */
    val onTakePhoto: (() -> Unit)? = null,
    /** Picks audio to send, kept as it is. */
    val onPickMusic: (() -> Unit)? = null,
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
    /** What the other person of a direct chat last said about being online. */
    presence: Presence? = null,
    /** You blocked the other person: instead of the composer there is «Разблокировать». */
    blocked: Boolean = false,
    /** Gesture hints already learned; by default none are offered. */
    hintsSeen: Set<GestureHint> = GestureHint.entries.toSet(),
    onHintDone: (GestureHint) -> Unit = {},
    /** What a double tap puts on a message: the «блик», or the emoji chosen in settings. */
    doubleTapReaction: String = GLINT_REACTION,
    /** Own username, to find messages that mention you; null when unknown. */
    me: String? = null,
    /** How many messages were unread when the chat was opened, before it was marked read. */
    unreadAtOpen: Int = conversation.unread,
    /** Opens at this message, from search, and lets light pass over it once. */
    focusMessageId: String? = null,
    underlay: @Composable BoxScope.() -> Unit = {},
) {
    val recording = remember { RecordingUi() }
    LaunchedEffect(recording.locked) { if (recording.locked) onHintDone(GestureHint.RecordLock) }
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
    val pinned = conversation.pinnedMessageIds.mapNotNull(byId::get)
    // The protection note, and possibly the key warning, come before the feed.
    val leading = 1 + (if (keyChanged) 1 else 0)
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
    var flashId by remember(conversation.id) { mutableStateOf<String?>(null) }
    val pinScope = rememberCoroutineScope()
    LaunchedEffect(messages.size, typing) {
        // Counted from the data, not the layout: on the first frame the list is not measured yet.
        val total = leading + feed.size + if (typing) 1 else 0
        val divider = feed.indexOf(FeedEntry.Unread)
        val focus = focusMessageId?.let { id -> feed.indexOfFirst { it is FeedEntry.Run && it.messages.any { m -> m.id == id } } } ?: -1
        when {
            !opened && focus >= 0 -> {
                listState.scrollToItem(focus + leading)
                flashId = focusMessageId
            }
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
            onReply = {
                replyToId = it.id
                editingId = null
                onHintDone(GestureHint.Reply)
            },
            onMenu = {
                haptics.menu()
                menuFor = it
            },
            onReact = react,
            onApplyTheme = callbacks.onApplyTheme,
            onButton = { message, button -> callbacks.onButton(message.id, button) },
            onDoubleTap = if (reactions) {
                { message ->
                    react(message, doubleTapReaction)
                    onHintDone(GestureHint.DoubleTap)
                }
            } else {
                null
            },
            onMention = callbacks.onOpenPerson,
            onTap = { message ->
                when {
                    selection.isNotEmpty() -> selection = if (message.id in selection) selection - message.id else selection + message.id
                    // A failed message answers a tap with what can be done about it.
                    message.isOwn && message.status == SendStatus.Failed -> menuFor = message
                }
            },
        )
    }

    CompositionLocalProvider(LocalMediaController provides media, LocalVeiledMedia provides conversation.request) {
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
                                callbacks.onCall, demo, presence,
                            )
                            ConnectionStatus(connection, callbacks.onReconnect)
                            if (pinned.isNotEmpty()) {
                                PinnedCapsule(
                                    pinned = pinned.toImmutableList(),
                                    onShow = { message ->
                                        val index = feed.indexOfFirst { it is FeedEntry.Run && it.messages.any { m -> m.id == message.id } }
                                        if (index >= 0) {
                                            pinScope.launch { listState.animateScrollToItem(index + leading) }
                                            flashId = null
                                            flashId = message.id
                                        }
                                    },
                                    onUnpin = callbacks.onPin?.let { pin -> { message -> pin(message.id, false) } },
                                )
                            }
                        }
                    }
                },
                bottom = {
                    val unblock = callbacks.onUnblock
                    val requestActions = callbacks.request
                    if (conversation.request && requestActions != null) {
                        BottomLens {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Button(
                                    text = stringResource(R.string.requests_delete),
                                    kind = ButtonKind.Secondary,
                                    onClick = { requestActions.onDelete(conversation) },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    text = stringResource(R.string.person_block),
                                    kind = ButtonKind.Secondary,
                                    onClick = { requestActions.onBlock(conversation) },
                                    modifier = Modifier.weight(1f),
                                )
                                Button(
                                    text = stringResource(R.string.requests_accept),
                                    onClick = { requestActions.onAccept(conversation) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    } else if (blocked && unblock != null) {
                        BottomLens {
                            Button(
                                text = stringResource(R.string.person_unblock),
                                kind = ButtonKind.Secondary,
                                onClick = unblock,
                                modifier = Modifier.fillMaxWidth().padding(6.dp),
                            )
                        }
                    } else if (readOnly) {
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
                            onTakePhoto = callbacks.onTakePhoto,
                            onPickMusic = callbacks.onPickMusic,
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
                            val author = if (showAuthors && !own) authorName(group.first().authorId) else null
                            val column: @Composable (Modifier) -> Unit = { columnModifier ->
                                MessageColumn(
                                    modifier = columnModifier,
                                    messages = group,
                                    author = author,
                                    fresh = remember(group) { group.map { it.id }.filterNot { it in initialIds }.toSet() },
                                    quoteText = { id -> byId[id]?.let { quoted -> quoteOf(quoted, you, authorName) } },
                                    actions = actions,
                                    selection = selection,
                                    flashId = flashId,
                                )
                            }
                            if (author == null) {
                                column(itemMotion)
                            } else {
                                // One face per column, at its foot, next to the latest message.
                                Row(itemMotion, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Avatar(
                                        name = author,
                                        size = ColumnAvatar,
                                        key = group.first().authorId,
                                        modifier = Modifier.padding(bottom = 2.dp),
                                    )
                                    column(Modifier.weight(1f))
                                }
                            }
                        }
                        if (typing) item(key = "typing") { TypingDots() }
                    }
                    val shownDay by remember(feed) {
                        derivedStateOf {
                            val visible = listState.layoutInfo.visibleItemsInfo
                            val first = visible.firstOrNull { it.index >= leading } ?: return@derivedStateOf null
                            feed.getOrNull(first.index - leading)?.let { entry ->
                                when (entry) {
                                    is FeedEntry.Day -> entry.date
                                    is FeedEntry.Run -> entry.messages.first().sentAt.atZone(ZoneId.systemDefault()).toLocalDate()
                                    FeedEntry.Unread -> null
                                }
                            }
                        }
                    }
                    val othersWrote = messages.any { !it.isOwn }
                    val writable = !readOnly
                    val hint = when {
                        selection.isNotEmpty() -> null
                        GestureHint.Reply !in hintsSeen && othersWrote && writable -> GestureHint.Reply
                        GestureHint.DoubleTap !in hintsSeen && othersWrote && reactions -> GestureHint.DoubleTap
                        GestureHint.RecordLock !in hintsSeen && recorders != null && writable -> GestureHint.RecordLock
                        else -> null
                    }
                    HintCapsule(
                        text = hint?.let { hintText(it) },
                        onClose = { hint?.let(onHintDone) },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = Blesk.dimens.gutter)
                            .padding(bottom = padding.calculateBottomPadding() + 12.dp),
                    )
                    ScrollDate(
                        date = shownDay,
                        scrolling = listState.isScrollInProgress,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = padding.calculateTopPadding() + 8.dp),
                    )
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
                    onPin = callbacks.onPin?.let { pin -> { pin(message.id, message.id !in conversation.pinnedMessageIds) } },
                    pinned = message.id in conversation.pinnedMessageIds,
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

/**
 * The chat's top lens: avatar and name, a small lock beside the name when the chat is end-to-end
 * encrypted, and the status underneath. The whole header opens the profile; on the right there
 * are only the calls. The lock is never the only sign: TalkBack reads the protection out loud.
 */
@Composable
private fun ChatHeader(
    conversation: Conversation,
    typing: Boolean,
    onBack: () -> Unit,
    onOpenTheme: () -> Unit,
    onOpenInfo: (() -> Unit)?,
    onCall: ((Boolean) -> Unit)?,
    demo: Boolean,
    presence: Presence?,
) {
    val protected = conversation.protection == Protection.EndToEnd
    val now = Instant.now()
    val encryption = stringResource(if (protected) R.string.chat_e2ee_spoken else R.string.chat_not_e2ee_spoken)
    val status = when {
        typing -> stringResource(R.string.chats_typing)
        presence?.isOnline(now) == true -> stringResource(R.string.chat_online)
        conversation.timerSeconds > 0 -> stringResource(R.string.chat_timer, timerLabel(conversation.timerSeconds))
        conversation.kind == ConversationKind.Saved -> stringResource(R.string.chat_saved_status)
        conversation.kind == ConversationKind.Bot -> stringResource(R.string.chat_bot_label)
        conversation.kind == ConversationKind.Channel ->
            pluralStringResource(R.plurals.chats_subscribers, conversation.memberCount, conversation.memberCount)
        conversation.kind == ConversationKind.Group || conversation.kind == ConversationKind.Room ->
            pluralStringResource(R.plurals.chats_members, conversation.memberCount, conversation.memberCount)
        presence?.wasRecent(now) == true -> stringResource(R.string.chat_recently)
        else -> "@${conversation.id}"
    }
    val openLabel = stringResource(R.string.chat_open_profile)
    TopLens(
        title = null,
        onBack = onBack,
        leading = {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = Blesk.dimens.touchTarget)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(role = Role.Button, onClickLabel = openLabel, onClick = onOpenInfo ?: onOpenTheme)
                    .semantics(mergeDescendants = true) { contentDescription = "${conversation.title}, $encryption, $status" }
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (conversation.faces.size >= 2) {
                    GroupAvatar(
                        faces = conversation.faces.map { it.name to it.id },
                        modifier = Modifier.sharedElement(avatarKey(conversation.id)),
                        size = 36.dp,
                    )
                } else {
                    Avatar(
                        name = conversation.title,
                        key = conversation.id,
                        size = 36.dp,
                        modifier = Modifier.sharedElement(avatarKey(conversation.id)),
                        protected = protected && conversation.kind != ConversationKind.Channel && conversation.kind != ConversationKind.Bot,
                        photoFile = conversation.photo,
                    )
                }
                Column(Modifier.weight(1f).padding(start = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = conversation.title,
                            style = Blesk.type.bodyStrong,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (protected) Icon(BleskIcons.Lock, contentDescription = null, tint = Blesk.colors.textSecondary, size = 14.dp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = status,
                            style = Blesk.type.caption,
                            color = if (typing) Blesk.colors.accent else Blesk.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false).semantics { liveRegion = LiveRegionMode.Polite },
                        )
                        if (demo) DemoTag()
                    }
                }
            }
        },
        actions = {
            if (onCall != null) {
                IconButton(BleskIcons.Phone, stringResource(R.string.chat_call), { onCall(false) })
                IconButton(BleskIcons.Video, stringResource(R.string.chat_video_call), { onCall(true) })
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
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { haptics.warn() }
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

/**
 * Pinned messages: a small capsule hanging from the top lens. A press goes to the message shown
 * and turns the capsule to the one pinned before it; with several, it shows which one it is.
 */
@Composable
private fun PinnedCapsule(pinned: ImmutableList<Message>, onShow: (Message) -> Unit, onUnpin: ((Message) -> Unit)?) {
    var shown by remember { mutableIntStateOf(pinned.lastIndex) }
    val index = shown.coerceIn(0, pinned.lastIndex)
    val message = pinned[index]
    val haptics = rememberHaptics()
    val number = stringResource(R.string.chat_pinned_number, index + 1, pinned.size)
    val unpinLabel = stringResource(R.string.chat_unpin)
    val title = stringResource(R.string.chat_pinned)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Tucked a little under the lens, so it reads as hanging from it rather than lying below.
            .padding(horizontal = 40.dp)
            .offset(y = (-4).dp)
            .lens(CircleShape, elevation = 8.dp)
            .clickable(role = Role.Button, onClickLabel = title) {
                haptics.tick()
                onShow(message)
                shown = if (index == 0) pinned.lastIndex else index - 1
            }
            .semantics {
                if (onUnpin != null) customActions = listOf(CustomAccessibilityAction(unpinLabel) { onUnpin(message); true })
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(BleskIcons.Pin, contentDescription = title, size = 16.dp, tint = Blesk.colors.textSecondary)
        Text(
            text = message.text.ifEmpty { title },
            style = Blesk.type.label,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        if (pinned.size > 1) {
            Text(text = number, style = Blesk.type.caption, color = Blesk.colors.textSecondary, maxLines = 1)
        }
    }
}

private val MENU_BLUR = 10.dp
private val ColumnAvatar = 28.dp
