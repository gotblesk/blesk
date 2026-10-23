package app.blesk.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
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
import androidx.compose.ui.draw.blur
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
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.HintCapsule
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.LocalHeaderFocus
import app.blesk.core.design.component.ProfileLensReach
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.headerFocusOf
import app.blesk.core.design.component.profileHeaderHeight
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.component.underSheet
import app.blesk.core.design.glass.LensAction
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.SearchLens
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.icon.LivingIcon
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.LocalClock
import app.blesk.core.motion.rememberHaptics
import app.blesk.core.network.NetworkProxy
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMenuActions
import app.blesk.feature.chats.ChatRowPlaces
import app.blesk.feature.chats.ConnectionStatus
import app.blesk.feature.chats.ContactActions
import app.blesk.feature.chats.ContactsTab
import app.blesk.feature.chats.Newcomer
import app.blesk.feature.chats.NewcomerChats
import app.blesk.feature.chats.RoomChannelHandlers
import app.blesk.feature.chats.groups.ChannelTarget
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.RoomChannelSheets
import app.blesk.feature.chats.hintText
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.SettingsList
import app.blesk.feature.settings.YouList
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.Right
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
    onOpenRoom: (String) -> Unit,
    onOpenArchive: () -> Unit,
    onOpenRequests: () -> Unit,
    contactActions: ContactActions,
    onAddContact: () -> Unit,
    settings: SettingsActions,
    onNeedCode: () -> Unit,
) {
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    // A channel's sheets opened from a room in the list: drawn over the lenses, which would otherwise cover them.
    var channelTarget by remember { mutableStateOf<Pair<String, ChannelTarget>?>(null) }
    val rowPlaces = remember { ChatRowPlaces() }
    // Under a chat's menu the list is darkened and softly blurred; the lifted row stays sharp.
    val menuBlur by animateDpAsState(if (menuFor != null) MenuBlur else 0.dp, Blesk.motion.settle(), label = "menuBlur")
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
    val photo = rememberProfilePhoto(container, profile?.photoVersion ?: 0L)
    val connection by remember(chats) { chats.connection }.collectAsStateWithLifecycle(initialValue = Connection.Online)
    val phrase by container.phrase.status.collectAsStateWithLifecycle()
    val sendByEnter by container.settings.sendByEnter.collectAsStateWithLifecycle(initialValue = false)
    val tapToRecord by container.settings.tapToRecord.collectAsStateWithLifecycle(initialValue = false)
    val vibration by container.settings.vibration.collectAsStateWithLifecycle(initialValue = VibrationLevel.Normal)
    val hintsSeen by container.settings.hintsSeen
        .map { seen -> GestureHint.entries.filter { it.name in seen }.toSet() }
        .collectAsStateWithLifecycle(initialValue = GestureHint.entries.toSet())
    val doubleTap by container.settings.doubleTapReaction.collectAsStateWithLifecycle(initialValue = GLINT_REACTION)
    val archiveFolded by container.settings.archiveFolded.collectAsStateWithLifecycle(initialValue = false)
    val proxied by NetworkProxy.current.collectAsStateWithLifecycle()
    val update by container.updater.available.collectAsStateWithLifecycle()
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
    val youCollapseAt = with(LocalDensity.current) { (profileHeaderHeight() - ProfileLensReach).toPx() }
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
    val clock = LocalClock.current
    val online = remember(presence) { presence.filterValues { it.isOnline(clock()) }.keys.toImmutableSet() }
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
        channelTarget = null
        if (searching && !inSearch) {
            searching = false
            searchOpen = false
            query.clearText()
        }
    }
    LensScaffold(
        modifier = Modifier.blur(menuBlur).underSheet(channelTarget != null),
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
            LensTabBar(tabs = tabs, selected = tab.ordinal, onSelect = { onSelectTab(HomeTab.entries[it]) }, action = action)
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
                // Tabs are one level: the old tab fades out completely, then the new one appears with a small
                // shift from the side of its tab. Two tabs are never on screen at once, so no text overlaps.
                val direction = if (targetState.ordinal > initialState.ordinal) 1 else -1
                val shift = if (motion.reduced) 0 else TAB_SHIFT_PERCENT
                val enter = fadeIn(motion.tabIn()) + slideInHorizontally(motion.tabIn()) { it * shift * direction / 100 }
                enter.togetherWith(fadeOut(motion.tabOut()))
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
                            now = remember(conversations) { clock() },
                            contentPadding = padding,
                            onOpenConversation = onOpenConversation,
                            onOpenChannel = onOpenChannel,
                            state = chatsList,
                            onLongPress = { menuFor = it },
                            places = rowPlaces,
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
                            header = if (phrase.overdue(System.currentTimeMillis())) {
                                { item(key = "phrase") { PhraseBanner(settings.onOpenPhrase) } }
                            } else {
                                null
                            },
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
                    val groups = chats as? GroupController
                    val roomIds = rooms.map { it.id }
                    val roomStates by remember(roomIds, groups) {
                        if (groups == null || roomIds.isEmpty()) {
                            flowOf(emptyMap())
                        } else {
                            combine(roomIds.map(groups::group)) { states -> states.filterNotNull().associateBy { it.id } }
                        }
                    }.collectAsStateWithLifecycle(initialValue = emptyMap())
                    ChatList(
                        conversations = rooms.toImmutableList(),
                        typing = typing.toImmutableSet(),
                        now = remember(conversations) { clock() },
                        contentPadding = padding,
                        onOpenConversation = onOpenConversation,
                        onOpenChannel = onOpenChannel,
                        state = roomsList,
                        // A closed room's long press opens its details; open rooms keep the menu.
                        onLongPress = { room -> if (room.id in roomStates) onOpenRoom(room.id) else menuFor = room },
                        onReorderRooms = chats::reorderPinned,
                        roomChannels = groups?.let { controller ->
                            RoomChannelHandlers(
                                manages = { id -> roomStates[id]?.let { GroupPolicy.can(it, controller.me, Right.Channels) } == true },
                                onAdd = { id -> channelTarget = id to ChannelTarget.Add },
                                onMenu = { id, channel -> channelTarget = id to ChannelTarget.Menu(channel.id.substringAfter(':')) },
                            )
                        },
                    )
                }
                HomeTab.Settings -> SettingsList(
                    serverHost = serverHost,
                    debugUnlocked = debugUnlocked,
                    contentPadding = padding,
                    actions = settings,
                    scroll = settingsScroll,
                    sendByEnter = sendByEnter,
                    onSendByEnter = { enabled -> scope.launch { container.settings.setComposer(sendByEnter = enabled) } },
                    tapToRecord = tapToRecord,
                    onTapToRecord = { enabled -> scope.launch { container.settings.setComposer(tapToRecord = enabled) } },
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
                            phraseUnchecked = phrase.unchecked,
                        )
                    }
                }
            }
        }
    }
    menuFor?.let { conversation ->
        ConversationMenu(
            container = container,
            chats = chats,
            conversation = conversation,
            all = conversations,
            anchor = rowPlaces[conversation.id],
            onPinLimit = onPinLimit,
            onOpen = onOpenConversation,
            onDismiss = { menuFor = null },
            onNeedCode = onNeedCode,
        )
    }
    val (roomId, target) = channelTarget ?: return
    val groups = chats as? GroupController ?: return
    RoomListChannelSheets(groups, roomId, target) { next -> channelTarget = next?.let { roomId to it } }
}

/**
 * The sheets of one of [roomId]'s channels, opened from the room in the list. A room gone meanwhile, dissolved or left,
 * closes them: kept open with nothing drawn, they would leave the list hidden from TalkBack under no sheet at all.
 */
@Composable
private fun RoomListChannelSheets(groups: GroupController, roomId: String, target: ChannelTarget, onTarget: (ChannelTarget?) -> Unit) {
    // Wrapped, so that a room not read yet is told apart from one that is gone.
    val read by remember(groups, roomId) { groups.group(roomId).map { RoomRead(it) } }.collectAsStateWithLifecycle(initialValue = null)
    val loaded = read ?: return
    val room = loaded.state
    if (room == null) {
        LaunchedEffect(Unit) { onTarget(null) }
        return
    }
    RoomChannelSheets(
        state = room,
        me = groups.me,
        actions = GroupActions(
            addMembers = { groups.addMembers(roomId, it) },
            addChannel = { title, kind, adminsOnly -> groups.addChannel(roomId, title, kind, adminsOnly) },
            removeChannel = { groups.removeChannel(roomId, it) },
            change = { groups.change(roomId, it) },
            leave = { groups.leave(roomId) },
        ),
        target = target,
        onTarget = onTarget,
    )
}

/** A room's state as read from the phone: null inside once the room is gone. */
private class RoomRead(val state: GroupState?)

/** The menu's actions; pinning past [MAX_PINNED] in the same list is refused with [onPinLimit]. */
internal fun chatMenuActions(
    chats: ChatRepository,
    conversation: Conversation,
    all: List<Conversation>,
    onPinLimit: () -> Unit,
    onDelete: ((forBoth: Boolean) -> Unit)? = null,
) = ChatMenuActions(
    onPin = { pin ->
        val pinnedHere = all.count { it.pinned && it.archived == conversation.archived && it.kind != ConversationKind.Room }
        if (pin && pinnedHere >= MAX_PINNED) onPinLimit() else chats.setChatPinned(conversation.id, pin)
    },
    onArchive = { chats.setArchived(conversation.id, it) },
    onMute = { chats.mute(conversation.id, it) },
    onMarkUnread = { unread -> if (unread) chats.markUnread(conversation.id, true) else chats.markRead(conversation.id) },
    onDelete = onDelete,
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

/** Reads the stored header choice; anything unknown falls back to the first light. */
internal fun headerStyleOf(stored: String): HeaderStyle {
    val (kind, value) = stored.split(':').let { it.first() to it.getOrNull(1)?.toIntOrNull() }
    return when (kind) {
        "photo" -> HeaderStyle.Photo
        "solid" -> HeaderStyle.Solid(value ?: 0)
        else -> HeaderStyle.Light(value ?: 0)
    }
}

/** A day after «Позже», over the chats until the phrase is written down. */
@Composable
private fun PhraseBanner(onOpen: () -> Unit) {
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 8.dp)) {
        ListRow(
            title = stringResource(SettingsR.string.you_phrase_reminder),
            subtitle = stringResource(SettingsR.string.you_phrase_reminder_hint),
            icon = BleskIcons.Lock,
            onClick = onOpen,
        )
    }
}

private val MenuBlur = 12.dp
