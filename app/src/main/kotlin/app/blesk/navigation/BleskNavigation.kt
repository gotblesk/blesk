package app.blesk.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.calls.CallActivity
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.AppMode
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.network.ServerAddress
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.chats.media.MediaViewer
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.security.WipeScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.ConnectOutcome
import app.blesk.feature.settings.License
import app.blesk.feature.settings.LicenseTextScreen
import app.blesk.feature.settings.LicensesScreen
import app.blesk.feature.settings.ServerScreen
import app.blesk.feature.settings.ServerState
import app.blesk.feature.settings.SettingsActions
import app.blesk.shared.appearance.Appearance
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class ConversationKey(val id: String) : NavKey

@Serializable
data class VoiceChannelKey(val roomId: String, val channelId: String) : NavKey

@Serializable
data object AppearanceKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object DebugKey : NavKey

@Serializable
data class MediaKey(val conversationId: String, val messageId: String) : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data class NewGroupKey(val kind: NewKind = NewKind.Group) : NavKey

@Serializable
data object NewChatKey : NavKey

@Serializable
data object ArchiveKey : NavKey

@Serializable
data object RoomsAddKey : NavKey

@Serializable
data object ScanKey : NavKey

@Serializable
data class GroupInfoKey(val groupId: String) : NavKey

@Serializable
data class SpaceInfoKey(val handle: String) : NavKey

@Serializable
data object SearchKey : NavKey

@Serializable
data object BotsKey : NavKey

@Serializable
data class SafetyKey(val username: String) : NavKey

@Serializable
data class ChatInfoKey(val id: String) : NavKey

@Serializable
data class ForwardKey(val from: String, val messageIds: List<String>) : NavKey

@Serializable
data object NotificationsKey : NavKey

@Serializable
data object StorageKey : NavKey

@Serializable
data object ServerKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data class PinChangeKey(val requireCurrent: Boolean) : NavKey

@Serializable
data object PinDisableKey : NavKey

@Serializable
data object WipeKey : NavKey

@Serializable
data object LicensesKey : NavKey

@Serializable
data class LicenseKey(val license: License) : NavKey

@Composable
fun BleskNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(HomeKey)
    val scope = rememberCoroutineScope()
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val debugUnlocked by container.settings.debugUnlocked.collectAsStateWithLifecycle(initialValue = false)
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    val demo = mode is AppMode.Demo
    val context = LocalContext.current
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    val openRequest by container.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        container.openRequest.value = null
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(ConversationKey(id))
    }

    LaunchedEffect(backStack.size) {
        container.eventLog.record(EventCode.ScreenOpened, backStack.size.toLong())
    }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryProvider = entryProvider {
            entry<HomeKey> {
                var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
                Home(
                    container = container,
                    tab = tab,
                    demo = demo,
                    serverHost = (mode as? AppMode.Server)?.url?.removePrefix("https://"),
                    debugUnlocked = debugUnlocked,
                    onSelectTab = { tab = it },
                    onOpenConversation = { backStack.add(ConversationKey(it)) },
                    onNewChat = { backStack.add(NewChatKey) },
                    onAddRoom = { backStack.add(RoomsAddKey) },
                    onSearch = { backStack.add(SearchKey) },
                    onOpenArchive = { backStack.add(ArchiveKey) },
                    onOpenChannel = { room, channel ->
                        when {
                            channel.kind != RoomChannelKind.Voice -> backStack.add(ConversationKey(channel.id))
                            // The demo has nobody to talk to, so its voice channels stay a picture.
                            demo -> backStack.add(VoiceChannelKey(room.id, channel.id))
                            else -> {
                                val title = "${room.title} · ${channel.title}"
                                val channelId = channel.id.substringAfter(':')
                                context.startActivity(CallActivity.voice(context, room.id, channelId, channel.id, title))
                            }
                        }
                    },
                    settings = SettingsActions(
                        onOpenProfile = { backStack.add(ProfileKey) },
                        onOpenAppearance = { backStack.add(AppearanceKey) },
                        onOpenPrivacy = { backStack.add(PrivacyKey) },
                        onOpenAbout = { backStack.add(AboutKey) },
                        onOpenDebug = { backStack.add(DebugKey) },
                        onOpenServer = { backStack.add(ServerKey) },
                        onOpenBots = { backStack.add(BotsKey) },
                        onOpenNotifications = { backStack.add(NotificationsKey) },
                        onOpenStorage = { backStack.add(StorageKey) },
                    ),
                )
            }
            entry<ProfileKey> { ProfileRoute(container, back) }
            entry<ServerKey> {
                val server = mode as? AppMode.Server
                ServerScreen(
                    connected = server?.let { ServerState(it.url, it.username, it.pin != null) },
                    onConnect = { raw, username -> connect(container, raw, username) },
                    onBack = back,
                )
            }
            entry<PrivacyKey> {
                PrivacyRoute(
                    container = container,
                    onSetPin = { backStack.add(PinChangeKey(requireCurrent = false)) },
                    onChangePin = { backStack.add(PinChangeKey(requireCurrent = true)) },
                    onDisablePin = { backStack.add(PinDisableKey) },
                    onWipe = { backStack.add(WipeKey) },
                    onBack = back,
                )
            }
            entry<PinChangeKey> { key -> PinChangeRoute(container, key.requireCurrent, back) }
            entry<PinDisableKey> { PinDisableRoute(container, back) }
            entry<WipeKey> {
                val context = LocalContext.current
                WipeScreen(onWipe = { wipeEverything(context, container) }, onBack = back)
            }
            entry<LicensesKey> { LicensesScreen(onOpenLicense = { backStack.add(LicenseKey(it)) }, onBack = back) }
            entry<LicenseKey> { key -> LicenseTextScreen(key.license, onBack = back) }
            entry<ConversationKey> { key ->
                ConversationRoute(
                    container = container,
                    id = key.id,
                    appearance = appearance,
                    onOpenMedia = { backStack.add(MediaKey(key.id, it)) },
                    onOpenGroup = { backStack.add(GroupInfoKey(it)) },
                    onOpenSpace = { backStack.add(SpaceInfoKey(it)) },
                    onVerify = { backStack.add(ChatInfoKey(key.id)) },
                    onForward = { messageIds -> backStack.add(ForwardKey(key.id, messageIds)) },
                    onCall = { video -> context.startActivity(CallActivity.call(context, key.id, video)) },
                    onBack = back,
                )
            }
            entry<NewChatKey> {
                NewChatRoute(
                    container = container,
                    onOpenConversation = { id ->
                        back()
                        backStack.add(ConversationKey(id))
                    },
                    onScan = { backStack.add(ScanKey) },
                    onNewGroup = { kind -> backStack.add(NewGroupKey(kind)) },
                    onBack = back,
                )
            }
            entry<ArchiveKey> { ArchiveRoute(container, onOpenConversation = { backStack.add(ConversationKey(it)) }, onBack = back) }
            entry<RoomsAddKey> { RoomsAddRoute(container, onCreate = { backStack.add(NewGroupKey(NewKind.Room)) }, onBack = back) }
            entry<ScanKey> {
                ScanRoute(container, onOpenConversation = { id ->
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    backStack.add(ConversationKey(id))
                }, onBack = back)
            }
            entry<NewGroupKey> { key ->
                NewGroupRoute(container, key.kind, onCreated = { id ->
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                    backStack.add(ConversationKey(id))
                }, onBack = back)
            }
            entry<GroupInfoKey> { key ->
                GroupInfoRoute(container, key.groupId, onLeft = {
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }, onBack = back)
            }
            entry<BotsKey> { BotsRoute(container, back) }
            entry<SafetyKey> { key -> SafetyRoute(container, key.username, back) }
            entry<ChatInfoKey> { key -> ChatInfoRoute(container, key.id, onVerify = { backStack.add(SafetyKey(key.id)) }, onBack = back) }
            entry<ForwardKey> { key -> ForwardRoute(container, key.from, key.messageIds, onDone = back, onBack = back) }
            entry<NotificationsKey> { NotificationsRoute(container, back) }
            entry<StorageKey> { StorageRoute(back) }
            entry<SearchKey> { SearchRoute(container, onOpen = { backStack.add(ConversationKey(it)) }, onBack = back) }
            entry<SpaceInfoKey> { key ->
                SpaceInfoRoute(container, key.handle, onLeft = {
                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                }, onBack = back)
            }
            entry<MediaKey> { key ->
                val chats = container.chats.collectAsStateWithLifecycle().value ?: return@entry
                val messages by chats.messages(key.conversationId).collectAsStateWithLifecycle(initialValue = null)
                val attachment = messages?.firstOrNull { it.id == key.messageId }?.attachment
                if (attachment != null) MediaViewer(attachment, onBack = back)
            }
            entry<VoiceChannelKey> { key ->
                val chats = container.chats.collectAsStateWithLifecycle().value ?: return@entry
                val channel by chats.conversation(key.roomId)
                    .map { room -> room?.channels?.firstOrNull { it.id == key.channelId } }
                    .collectAsStateWithLifecycle(initialValue = null)
                channel?.let { VoiceChannelScreen(channel = it, onBack = back) }
            }
            entry<AppearanceKey> {
                AppearanceRoute(container, appearance, back)
            }
            entry<AboutKey> {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    debugUnlocked = debugUnlocked,
                    onOpenLicenses = { backStack.add(LicensesKey) },
                    onUnlockDebug = {
                        scope.launch { container.settings.setDebugUnlocked(true) }
                        container.eventLog.record(EventCode.DebugUnlocked)
                    },
                    onBack = back,
                )
            }
            entry<DebugKey> {
                val entries by container.eventLog.entries.collectAsStateWithLifecycle()
                var crashVersion by remember { mutableIntStateOf(0) }
                val crash = remember(crashVersion) { container.crashStore.read() }
                DebugScreen(
                    entries = entries.toImmutableList(),
                    crash = crash,
                    appInfo = "blesk ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.SDK_INT}",
                    onClearCrash = {
                        container.crashStore.clear()
                        container.eventLog.record(EventCode.CrashCleared)
                        crashVersion++
                    },
                    onClearJournal = container.eventLog::clear,
                    onBack = back,
                )
            }
        },
    )
}

private suspend fun connect(container: AppContainer, raw: String, username: String): ConnectOutcome {
    val address = ServerAddress.parse(raw) ?: return ConnectOutcome.BadAddress
    return when (container.connect(address, username)) {
        RegisterResult.Ok -> ConnectOutcome.Ok
        RegisterResult.NameTaken -> ConnectOutcome.NameTaken
        RegisterResult.NameInvalid -> ConnectOutcome.NameInvalid
        RegisterResult.Unreachable -> ConnectOutcome.Unreachable
    }
}
