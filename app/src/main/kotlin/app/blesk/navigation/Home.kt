package app.blesk.navigation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.GroupController
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.LensAction
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ChatMenu
import app.blesk.feature.chats.ChatMenuActions
import app.blesk.feature.chats.EmptyChats
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.SettingsList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import java.time.Instant

enum class HomeTab { Chats, Rooms, You }

/** The three tabs under the two lenses: chats, rooms and you. */
@Composable
internal fun Home(
    container: AppContainer,
    tab: HomeTab,
    demo: Boolean,
    serverHost: String?,
    debugUnlocked: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenConversation: (String) -> Unit,
    onNewChat: () -> Unit,
    onAddRoom: () -> Unit,
    onSearch: () -> Unit,
    onOpenChannel: (Conversation, RoomChannel) -> Unit,
    onOpenArchive: () -> Unit,
    settings: SettingsActions,
) {
    var menuFor by remember { mutableStateOf<Conversation?>(null) }
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
    val photo = rememberProfilePhoto(container, profile?.photoVersion ?: 0L)
    val online by remember(chats) { chats.online }.collectAsStateWithLifecycle(initialValue = true)
    val sendByEnter by container.settings.sendByEnter.collectAsStateWithLifecycle(initialValue = false)
    val vibration by container.settings.vibration.collectAsStateWithLifecycle(initialValue = VibrationLevel.Normal)
    val scope = rememberCoroutineScope()
    val tabs = persistentListOf(
        LensTab(stringResource(R.string.tab_chats), BleskIcons.Chats),
        LensTab(stringResource(app.blesk.feature.chats.R.string.rooms_title), BleskIcons.Rooms),
        LensTab(stringResource(app.blesk.feature.settings.R.string.you_title), BleskIcons.Person) {
            val me = profile
            Avatar(me?.name?.ifBlank { "?" } ?: "?", size = 24.dp, photo = photo, key = me?.username?.ifBlank { me.name } ?: "")
        },
    )
    val chatsList = rememberLazyListState()
    val roomsList = rememberLazyListState()
    val youScroll = rememberScrollState()
    val collapsed = when (tab) {
        HomeTab.Chats -> chatsList.firstVisibleItemIndex > 0 || chatsList.firstVisibleItemScrollOffset > COLLAPSE_AFTER
        HomeTab.Rooms -> roomsList.firstVisibleItemIndex > 0 || roomsList.firstVisibleItemScrollOffset > COLLAPSE_AFTER
        HomeTab.You -> youScroll.value > COLLAPSE_AFTER
    }
    val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by chats.typing.collectAsStateWithLifecycle()
    LensScaffold(
        top = {
            TopLens(
                title = if (tab == HomeTab.Chats && !online) {
                    stringResource(app.blesk.feature.chats.R.string.chats_waiting)
                } else {
                    tabs[tab.ordinal].label
                },
                collapsed = collapsed,
                actions = {
                    when (tab) {
                        HomeTab.Chats ->
                            IconButton(BleskIcons.Search, stringResource(app.blesk.feature.chats.R.string.search_title), onSearch)
                        HomeTab.Rooms, HomeTab.You -> Unit
                    }
                },
            )
        },
        bottom = {
            val action = when (tab) {
                HomeTab.Chats -> LensAction(BleskIcons.NewChat, stringResource(app.blesk.feature.chats.R.string.new_chat_title), onNewChat)
                HomeTab.Rooms -> if (chats is GroupController) {
                    LensAction(BleskIcons.Plus, stringResource(app.blesk.feature.chats.R.string.rooms_add), onAddRoom)
                } else {
                    null
                }
                HomeTab.You -> LensAction(BleskIcons.Edit, stringResource(R.string.you_edit), settings.onOpenProfile)
            }
            LensTabBar(tabs = tabs, selected = tab.ordinal, onSelect = { onSelectTab(HomeTab.entries[it]) }, action = action)
        },
    ) { padding ->
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
                    val list = conversations.filter { it.kind != ConversationKind.Room && !it.archived }
                    val archived = conversations.count { it.archived }
                    if (list.isEmpty() && archived == 0 && !demo) {
                        EmptyChats(padding)
                        return@AnimatedContent
                    }
                    ChatList(
                        conversations = list.toImmutableList(),
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
                        onOpenArchive = onOpenArchive,
                    )
                }
                HomeTab.Rooms -> {
                    val rooms = conversations.filter { it.kind == ConversationKind.Room }
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
                    )
                }
                HomeTab.You -> {
                    val current = profile ?: return@AnimatedContent
                    SettingsList(
                        serverHost = serverHost,
                        debugUnlocked = debugUnlocked,
                        profile = current,
                        photo = photo,
                        contentPadding = padding,
                        actions = settings,
                        demo = demo,
                        scroll = youScroll,
                        sendByEnter = sendByEnter,
                        onSendByEnter = { enabled -> scope.launch { container.settings.setSendByEnter(enabled) } },
                        vibration = vibration,
                        onVibration = { level -> scope.launch { container.settings.setVibration(level) } },
                    )
                }
            }
        }
    }
    menuFor?.let { conversation ->
        ChatMenu(
            conversation = conversation,
            now = System.currentTimeMillis(),
            actions = chatMenuActions(chats, conversation),
            onDismiss = { menuFor = null },
        )
    }
}

internal fun chatMenuActions(chats: ChatRepository, conversation: Conversation) = ChatMenuActions(
    onPin = { chats.setChatPinned(conversation.id, it) },
    onArchive = { chats.setArchived(conversation.id, it) },
    onMute = { chats.mute(conversation.id, it) },
    onMarkUnread = { unread -> if (unread) chats.markUnread(conversation.id, true) else chats.markRead(conversation.id) },
)

/** Read becomes unread and back, as the swipe and the menu both offer. */
internal fun toggleRead(chats: ChatRepository, conversation: Conversation) {
    if (conversation.unread > 0 || conversation.markedUnread) chats.markRead(conversation.id) else chats.markUnread(conversation.id, true)
}

private const val COLLAPSE_AFTER = 24

private const val TAB_SHIFT_PERCENT = 6
