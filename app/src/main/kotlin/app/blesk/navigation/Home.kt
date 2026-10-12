package app.blesk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.R
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.Connection
import app.blesk.core.data.GroupController
import app.blesk.core.data.MAX_PINNED
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.settings.GestureHint
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.HintCapsule
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.LocalHeaderFocus
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.headerFocusOf
import app.blesk.core.design.component.profileHeaderHeight
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.glass.LensAction
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.SearchLens
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.icon.LivingIcon
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.core.network.NetworkProxy
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMenu
import app.blesk.feature.chats.ChatMenuActions
import app.blesk.feature.chats.ConnectionStatus
import app.blesk.feature.chats.ContactActions
import app.blesk.feature.chats.ContactsTab
import app.blesk.feature.chats.Newcomer
import app.blesk.feature.chats.NewcomerChats
import app.blesk.feature.chats.hintText
import app.blesk.feature.chats.media.MiniPlayer
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.SettingsList
import app.blesk.feature.settings.YouList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant
import app.blesk.feature.chats.R as ChatsR
import app.blesk.feature.settings.R as SettingsR

enum class HomeTab { Chats, Contacts, Rooms, Settings, You }

/** The five tabs under the two lenses: chats, contacts, rooms, settings and you. */
@Composable
internal fun Home(
    container: AppContainer,
    tab: HomeTab,
    demo: Boolean,
    serverHost: String?,
    debugUnlocked: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenMessage: (conversationId: String, messageId: String) -> Unit,
    onNewChat: () -> Unit,
    onAddRoom: () -> Unit,
    onOpenChannel: (Conversation, RoomChannel) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenRequests: () -> Unit,
    contactActions: ContactActions,
    onAddContact: () -> Unit,
    settings: SettingsActions,
) {
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
    val photo = rememberProfilePhoto(container, profile?.photoVersion ?: 0L)
    val connection by remember(chats) { chats.connection }.collectAsStateWithLifecycle(initialValue = Connection.Online)
    val sendByEnter by container.settings.sendByEnter.collectAsStateWithLifecycle(initialValue = false)
    val vibration by container.settings.vibration.collectAsStateWithLifecycle(initialValue = VibrationLevel.Normal)
    val hintsSeen by container.settings.hintsSeen
        .map { seen -> GestureHint.entries.filter { it.name in seen }.toSet() }
        .collectAsStateWithLifecycle(initialValue = GestureHint.entries.toSet())
    val doubleTap by container.settings.doubleTapReaction.collectAsStateWithLifecycle(initialValue = GLINT_REACTION)
    val archiveFolded by container.settings.archiveFolded.collectAsStateWithLifecycle(initialValue = false)
    val proxied by NetworkProxy.current.collectAsStateWithLifecycle()
    val update by container.updater.available.collectAsStateWithLifecycle()
    val playback by container.audioPlayer.playback.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val onPinLimit = rememberPinLimit()
    val tabs = persistentListOf(
        LensTab(stringResource(R.string.tab_chats), BleskIcons.Chats, LivingIcon.Fill),
        LensTab(stringResource(app.blesk.feature.chats.R.string.contacts_title), BleskIcons.People, LivingIcon.Gather),
        LensTab(stringResource(app.blesk.feature.chats.R.string.rooms_title), BleskIcons.Rooms, LivingIcon.Stick),
        LensTab(stringResource(app.blesk.feature.settings.R.string.settings_title), BleskIcons.Settings, LivingIcon.Slide),
        LensTab(stringResource(app.blesk.feature.settings.R.string.you_title), BleskIcons.Person) {
            val me = profile
            Box {
                Avatar(me?.name?.ifBlank { "?" } ?: "?", size = 24.dp, photo = photo, key = me?.username?.ifBlank { me.name } ?: "")
                // A newer build waits in «Ты».
                if (update != null) Box(Modifier.align(Alignment.TopEnd).size(8.dp).background(Blesk.colors.accent, CircleShape))
            }
        },
    )
    val youCollapseAt = with(LocalDensity.current) { (profileHeaderHeight() - YouLensReach).toPx() }
    val headerPhoto = rememberHeaderPhoto(container, profile?.headerVersion ?: 0L)
    val chatsList = rememberLazyListState()
    val contactsList = rememberLazyListState()
    val roomsList = rememberLazyListState()
    val settingsScroll = rememberScrollState()
    val youScroll = rememberScrollState()
    val collapsed = when (tab) {
        HomeTab.Chats -> chatsList.scrolledPast()
        HomeTab.Contacts -> contactsList.scrolledPast()
        HomeTab.Rooms -> roomsList.scrolledPast()
        HomeTab.Settings -> settingsScroll.value > COLLAPSE_AFTER
        // «Ты» has no title of its own; the lens appears once the avatar has risen into it.
        HomeTab.You -> youScroll.value > youCollapseAt
    }
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by chats.typing.collectAsStateWithLifecycle()
    val presence by chats.presence.collectAsStateWithLifecycle()
    val online = remember(presence) { presence.filterValues { it.isOnline(Instant.now()) }.keys.toImmutableSet() }
    var searching by rememberSaveable { mutableStateOf(false) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    val query = rememberTextFieldState()
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    // The search lens serves chats and contacts; on contacts it only narrows the list below.
    val searchable = tab == HomeTab.Chats || tab == HomeTab.Contacts
    val inSearch = searching && searchable
    BackHandler(enabled = inSearch && searchOpen) { searchOpen = false }
    // A query belongs to the tab it was typed on; switching tabs starts clean.
    LaunchedEffect(tab) {
        if (searching && !inSearch) {
            searching = false
            searchOpen = false
            query.clearText()
        }
    }
    LensScaffold(
        top = {
            if (inSearch) {
                SearchLens(
                    state = query,
                    hint = stringResource(if (tab == HomeTab.Contacts) ChatsR.string.contacts_search else ChatsR.string.search_field),
                    closeLabel = stringResource(app.blesk.feature.chats.R.string.search_close),
                    open = searchOpen,
                    onClose = { searchOpen = false },
                    onClosed = {
                        searching = false
                        query.clearText()
                    },
                )
                return@LensScaffold
            }
            Column {
                if (tab == HomeTab.You) {
                    val me = profile
                    if (collapsed && me != null) {
                        TopLens(
                            title = me.name.ifBlank { tabs[tab.ordinal].label },
                            leading = {
                                Avatar(me.name.ifBlank { "?" }, size = 36.dp, photo = photo, key = me.username.ifBlank { me.name })
                            },
                        )
                    }
                    ConnectionStatus(connection, chats::reconnect)
                    return@Column
                }
                TopLens(
                    title = tabs[tab.ordinal].label,
                    collapsed = collapsed,
                    actions = {
                        // The shield says the app's traffic goes through a proxy; it opens the setting.
                        if (proxied != null) IconButton(BleskIcons.Shield, stringResource(SettingsR.string.proxy_on), settings.onOpenServer)
                        if (searchable) {
                            IconButton(BleskIcons.Search, stringResource(app.blesk.feature.chats.R.string.search_title), {
                                searching = true
                                searchOpen = true
                            })
                        }
                    },
                )
                ConnectionStatus(connection, chats::reconnect)
            }
        },
        bottom = {
            val action = when (tab) {
                HomeTab.Chats -> LensAction(BleskIcons.NewChat, stringResource(app.blesk.feature.chats.R.string.new_chat_title), onNewChat)
                HomeTab.Contacts -> LensAction(BleskIcons.Invite, stringResource(ChatsR.string.contacts_add), onAddContact)
                HomeTab.Rooms -> if (chats is GroupController) {
                    LensAction(BleskIcons.Plus, stringResource(app.blesk.feature.chats.R.string.rooms_add), onAddRoom)
                } else {
                    null
                }
                // Settings have nothing to create, so the capsule draws back into the lens.
                HomeTab.Settings -> null
                HomeTab.You -> LensAction(BleskIcons.Edit, stringResource(R.string.you_edit), settings.onOpenProfile)
            }
            Column {
                MiniPlayer(
                    playback = playback,
                    onToggle = { playback.path?.let(container.audioPlayer::toggle) },
                    onStop = container.audioPlayer::stop,
                )
                LensTabBar(tabs = tabs, selected = tab.ordinal, onSelect = { onSelectTab(HomeTab.entries[it]) }, action = action)
            }
        },
    ) { padding ->
        if (inSearch && tab == HomeTab.Chats) {
            SearchPanel(container, query, padding, onOpenConversation, onOpenMessage)
            return@LensScaffold
        }
        val motion = Blesk.motion
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                // Tabs are one level: a dissolve with a small shift towards the tab, never a whole screen sliding in.
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val shift = if (motion.reduced) 0 else TAB_SHIFT_PERCENT
                val enter = fadeIn(motion.transition()) + slideInHorizontally(motion.transition()) { it * shift * direction / 100 }
                val exit = fadeOut(motion.transition()) + slideOutHorizontally(motion.transition()) { -it * shift * direction / 100 }
                enter.togetherWith(exit)
            },
            label = "tab",
        ) { shown ->
            when (shown) {
                HomeTab.Chats -> {
                    val hidden = container.notices.hidden
                    val list = conversations.filter { it.kind != ConversationKind.Room && !it.archived && it.id !in hidden }
                    val archived = conversations.count { it.archived }
                    val friends = list.filter { it.kind != ConversationKind.Saved && !it.request }
                    // Once the newcomer screen has shown, it stays until the first friend has flown in and stuck.
                    val welcoming = rememberSaveable { mutableStateOf(false) }
                    val requests = list.filter { it.request }
                    val alone = friends.isEmpty() && requests.isEmpty() && archived == 0 && !demo
                    if (alone && !welcoming.value) welcoming.value = true
                    val me = profile
                    if (welcoming.value && me != null) {
                        NewcomerChats(
                            me = Newcomer(me.name.ifBlank { me.username }, me.username, photo),
                            contentPadding = padding,
                            onFind = onNewChat,
                            onInvite = contactActions.onInvite,
                            friend = friends.firstOrNull()?.let { Newcomer(it.title, it.id, null) },
                            onSettled = { welcoming.value = false },
                        )
                        return@AnimatedContent
                    }
                    val pinnedCount = list.count { it.pinned }
                    val listHint = when {
                        GestureHint.PinnedDrag !in hintsSeen && pinnedCount >= 2 -> GestureHint.PinnedDrag
                        GestureHint.ArchivePull !in hintsSeen && archiveFolded && archived > 0 -> GestureHint.ArchivePull
                        else -> null
                    }
                    val markHint: (GestureHint) -> Unit = { hint -> scope.launch { container.settings.markHint(hint) } }
                    Box(Modifier.fillMaxSize()) {
                        ChatList(
                            conversations = list.filterNot { it.request }.toImmutableList(),
                            requests = requests.toImmutableList(),
                            onOpenRequests = onOpenRequests,
                            typing = typing.toImmutableSet(),
                            now = remember(conversations) { Instant.now() },
                            contentPadding = padding,
                            onOpenConversation = onOpenConversation,
                            onOpenChannel = onOpenChannel,
                            state = chatsList,
                            onLongPress = { menuFor = it },
                            onSwipeArchive = { chats.setArchived(it.id, true) },
                            onSwipeRead = { toggleRead(chats, it) },
                            archivedCount = archived,
                            online = online,
                            onOpenArchive = onOpenArchive,
                            onReorderPinned = { order ->
                                chats.reorderPinned(order)
                                markHint(GestureHint.PinnedDrag)
                            },
                            onSwipeUnpin = { chats.setChatPinned(it.id, false) },
                            archiveFolded = archiveFolded,
                            onFoldArchive = { folded -> scope.launch { container.settings.setArchiveFolded(folded) } },
                            onPullArchive = { markHint(GestureHint.ArchivePull) },
                        )
                        HintCapsule(
                            text = listHint?.let { hintText(it) },
                            onClose = { listHint?.let(markHint) },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = Blesk.dimens.gutter)
                                .padding(bottom = padding.calculateBottomPadding() + 12.dp),
                        )
                    }
                }
                HomeTab.Contacts -> ContactsTab(
                    contacts = contacts.toImmutableList(),
                    query = if (inSearch) query.text.toString() else "",
                    contentPadding = padding,
                    actions = contactActions,
                    state = contactsList,
                    online = online,
                    lastWritten = remember(conversations) {
                        conversations.filter { it.kind == ConversationKind.Direct }.associate { it.id to it.updatedAt }
                    },
                )
                HomeTab.Rooms -> {
                    // Archived rooms live in the archive with the chats; pinned ones lead, in the order given.
                    val hidden = container.notices.hidden
                    val rooms = conversations.filter { it.kind == ConversationKind.Room && !it.archived && it.id !in hidden }
                        .sortedWith(compareByDescending<Conversation> { it.pinned }.thenBy { if (it.pinned) it.pinRank else 0 })
                    if (rooms.isEmpty()) {
                        Text(
                            text = stringResource(app.blesk.feature.chats.R.string.rooms_empty),
                            color = Blesk.colors.textSecondary,
                            modifier = Modifier.padding(padding).padding(Blesk.dimens.gutter + 4.dp),
                        )
                        return@AnimatedContent
                    }
                    ChatList(
                        conversations = rooms.toImmutableList(),
                        typing = typing.toImmutableSet(),
                        now = remember(conversations) { Instant.now() },
                        contentPadding = padding,
                        onOpenConversation = onOpenConversation,
                        onOpenChannel = onOpenChannel,
                        state = roomsList,
                        onLongPress = { menuFor = it },
                        onReorderRooms = chats::reorderPinned,
                    )
                }
                HomeTab.Settings -> SettingsList(
                    serverHost = serverHost,
                    debugUnlocked = debugUnlocked,
                    contentPadding = padding,
                    actions = settings,
                    scroll = settingsScroll,
                    sendByEnter = sendByEnter,
                    onSendByEnter = { enabled -> scope.launch { container.settings.setSendByEnter(enabled) } },
                    vibration = vibration,
                    onVibration = { level -> scope.launch { container.settings.setVibration(level) } },
                    doubleTap = doubleTap,
                    onResetHints = { scope.launch { container.settings.resetHints() } },
                    onDoubleTap = { symbol -> scope.launch { container.settings.setDoubleTapReaction(symbol) } },
                    proxied = proxied != null,
                    versionName = BuildConfig.VERSION_NAME,
                )
                HomeTab.You -> {
                    val current = profile ?: return@AnimatedContent
                    CompositionLocalProvider(LocalHeaderFocus provides headerFocusOf(current.header)) {
                        YouList(
                            serverHost = serverHost,
                            profile = current,
                            photo = photo,
                            contentPadding = padding,
                            actions = settings,
                            demo = demo,
                            scroll = youScroll,
                            header = headerStyleOf(current.header),
                            headerPhoto = headerPhoto,
                            update = update?.title,
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { conversation ->
        ChatMenu(
            conversation = conversation,
            now = System.currentTimeMillis(),
            actions = chatMenuActions(chats, conversation, conversations, onPinLimit),
            onDismiss = { menuFor = null },
        )
    }
}

/** The menu's actions; pinning past [MAX_PINNED] in the same list is refused with [onPinLimit]. */
internal fun chatMenuActions(
    chats: ChatRepository,
    conversation: Conversation,
    all: List<Conversation>,
    onPinLimit: () -> Unit,
) = ChatMenuActions(
    onPin = { pin ->
        val pinnedHere = all.count { it.pinned && it.archived == conversation.archived && it.kind != ConversationKind.Room }
        if (pin && pinnedHere >= MAX_PINNED) onPinLimit() else chats.setChatPinned(conversation.id, pin)
    },
    onArchive = { chats.setArchived(conversation.id, it) },
    onMute = { chats.mute(conversation.id, it) },
    onMarkUnread = { unread -> if (unread) chats.markUnread(conversation.id, true) else chats.markRead(conversation.id) },
)

/** Says the pinned list is full, with the refusing buzz. */
@Composable
internal fun rememberPinLimit(): () -> Unit {
    val notify = rememberNotify()
    val haptics = rememberHaptics()
    val text = stringResource(ChatsR.string.chats_pin_limit)
    return {
        haptics.refuse()
        notify(text)
    }
}

/** Read becomes unread and back, as the swipe and the menu both offer. */
internal fun toggleRead(chats: ChatRepository, conversation: Conversation) {
    if (conversation.unread > 0 || conversation.markedUnread) chats.markRead(conversation.id) else chats.markUnread(conversation.id, true)
}

private const val COLLAPSE_AFTER = 24

private fun LazyListState.scrolledPast() = firstVisibleItemIndex > 0 || firstVisibleItemScrollOffset > COLLAPSE_AFTER

private const val TAB_SHIFT_PERCENT = 6

private val YouLensReach = 72.dp

/** Reads the stored header choice; anything unknown falls back to the first light. */
internal fun headerStyleOf(stored: String): HeaderStyle {
    val (kind, value) = stored.split(':').let { it.first() to it.getOrNull(1)?.toIntOrNull() }
    return when (kind) {
        "photo" -> HeaderStyle.Photo
        "solid" -> HeaderStyle.Solid(value ?: 0)
        else -> HeaderStyle.Light(value ?: 0)
    }
}
