package app.blesk.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.calls.CallActivity
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.motion.LocalSharedScope
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.theme.Blesk
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.network.NetworkProxy
import app.blesk.core.network.ServerAddress
import app.blesk.feature.chats.ContactActions
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.profile.MyQrScreen
import app.blesk.feature.profile.profileLink
import app.blesk.feature.security.WipeKind
import app.blesk.feature.security.WipeScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.ConnectOutcome
import app.blesk.feature.settings.License
import app.blesk.feature.settings.LicenseTextScreen
import app.blesk.feature.settings.LicensesScreen
import app.blesk.feature.settings.MetaballSandbox
import app.blesk.feature.settings.PrivacyPolicyScreen
import app.blesk.feature.settings.ProxyCheck
import app.blesk.feature.settings.ProxyControls
import app.blesk.feature.settings.ServerScreen
import app.blesk.feature.settings.ServerState
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.UpdateControls
import app.blesk.feature.settings.WhatsNewScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.toConfig
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class ConversationKey(val id: String, val focusMessageId: String? = null) : NavKey

@Serializable
data class VoiceChannelKey(val roomId: String, val channelId: String) : NavKey

@Serializable
data object AppearanceKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object DebugKey : NavKey

@Serializable
data class MediaKey(val conversationId: String, val messageId: String, val index: Int = 0) : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object MyQrKey : NavKey

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
data class WipeKey(val kind: WipeKind = WipeKind.Phone) : NavKey

@Serializable
data object LicensesKey : NavKey

@Serializable
data object PolicyKey : NavKey

@Serializable
data object SandboxKey : NavKey

@Serializable
data object UpdateKey : NavKey

@Serializable
data object WhatsNewKey : NavKey

private const val NOTES = "RELEASE_NOTES.md"

@Serializable
data object BlockedKey : NavKey

@Serializable
data object RequestsKey : NavKey

@Serializable
data object ShareKey : NavKey

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
    val openDirect: (String, (String) -> Unit) -> Unit = { username, then ->
        scope.launch { container.chats.value?.openDirect(username)?.let(then) }
    }

    val openRequest by container.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest) {
        val id = openRequest ?: return@LaunchedEffect
        container.openRequest.value = null
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(ConversationKey(id))
    }

    // A blesk:// link from another app: a profile opens its chat, an invite asks to join.
    val linkRequest by container.linkRequest.collectAsStateWithLifecycle()
    val ready = container.chats.collectAsStateWithLifecycle().value
    val notify = rememberNotify()
    val joinSent = stringResource(app.blesk.feature.chats.R.string.contacts_join_sent)
    val contactRemoved = stringResource(app.blesk.feature.chats.R.string.contacts_removed)
    val linkMissing = stringResource(app.blesk.feature.chats.R.string.link_not_found)
    LaunchedEffect(linkRequest, ready) {
        val link = linkRequest ?: return@LaunchedEffect
        val chats = ready ?: return@LaunchedEffect
        container.linkRequest.value = null
        val opened = find(chats, link, open = { backStack.add(ConversationKey(it)) }) { notify(joinSent) }
        if (!opened) notify(linkMissing)
    }
    val shareRequest by container.shareRequest.collectAsStateWithLifecycle()
    LaunchedEffect(shareRequest) {
        if (shareRequest != null && backStack.lastOrNull() != ShareKey) backStack.add(ShareKey)
    }

    LaunchedEffect(backStack.size) {
        container.eventLog.record(EventCode.ScreenOpened, backStack.size.toLong())
    }

    val motion = Blesk.motion
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedScope provides this) {
            // The in-app notice floats over every screen, under the status bar.
            Box(Modifier.fillMaxSize()) {
                val screenDecorator = remember {
                    NavEntryDecorator<Any>(decorate = { entry -> Screen { entry.Content() } })
                }
                NavDisplay(
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { screenEnter(motion) },
                    popTransitionSpec = { screenBack(motion, edge = null) },
                    predictivePopTransitionSpec = { edge -> screenBack(motion, edge) },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), screenDecorator),
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
                                onOpenMessage = { chat, message -> backStack.add(ConversationKey(chat, message)) },
                                onNewChat = { backStack.add(NewChatKey) },
                                onAddRoom = { backStack.add(RoomsAddKey) },
                                onOpenArchive = { backStack.add(ArchiveKey) },
                                onOpenRequests = { backStack.add(RequestsKey) },
                                onAddContact = { backStack.add(NewChatKey) },
                                contactActions = ContactActions(
                                    onOpen = { contact -> openDirect(contact.username) { backStack.add(ChatInfoKey(it)) } },
                                    onWrite = { contact -> openDirect(contact.username) { backStack.add(ConversationKey(it)) } },
                                    onCall = { contact ->
                                        openDirect(contact.username) { id -> context.startActivity(CallActivity.call(context, id, false)) }
                                    },
                                    onInvite = (mode as? AppMode.Server)?.let { server ->
                                        { scope.launch { invite(context, server.username, container.chats.value?.linkKey()) } }
                                    },
                                    onRemove = { contact ->
                                        container.notices.undoable(contactRemoved) {
                                            container.chats.value?.removeContact(contact.username)
                                        }
                                    },
                                ),
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
                                    onOpenUpdate = { backStack.add(UpdateKey) },
                                    onOpenPhrase = { backStack.add(PhraseKey()) },
                                    onOpenDevices = { backStack.add(DevicesKey) },
                                    onShowQr = { backStack.add(MyQrKey) },
                                    onDeleteAccount = if (demo) null else ({ backStack.add(WipeKey(WipeKind.DeleteAccount)) }),
                                    onShare = {
                                        scope.launch {
                                            val key = container.chats.value?.linkKey()
                                            shareProfile(context, container.settings.profile.first().username, key)
                                        }
                                    },
                                    onOpenStickers = { backStack.add(StickersKey) },
                                    onOpenSaved = {
                                        scope.launch {
                                            val chats = container.chats.value ?: return@launch
                                            chats.conversations.first().firstOrNull { it.kind == ConversationKind.Saved }
                                                ?.let { backStack.add(ConversationKey(it.id)) }
                                        }
                                    },
                                ),
                            )
                        }
                        entry<ProfileKey> { ProfileRoute(container, back) }
                        entry<MyQrKey> {
                            val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
                            val linkKey by produceState<String?>(null) { value = container.chats.value?.linkKey() }
                            MyQrScreen(profile?.username.orEmpty(), onBack = back, linkKey = linkKey)
                        }
                        entry<ServerKey> {
                            Box(Modifier.sharedBounds(screenKey("server"))) {
                                val server = mode as? AppMode.Server
                                val proxy by container.settings.proxy.collectAsStateWithLifecycle(ProxySetting())
                                ServerScreen(
                                    connected = server?.let { ServerState(it.url, it.username, it.pin != null) },
                                    onConnect = { raw, username ->
                                        connect(container, raw, username).also { outcome ->
                                            // A new account starts with its phrase: written down now, or put off on purpose.
                                            if (outcome == ConnectOutcome.Ok) backStack.add(PhraseKey(afterRegistration = true))
                                        }
                                    },
                                    onBack = back,
                                    proxy = ProxyControls(
                                        setting = proxy,
                                        onSave = { scope.launch { container.settings.setProxy(it) } },
                                        onCheck = { checkProxy(container, it) },
                                    ),
                                )
                            }
                        }
                        entry<PrivacyKey> {
                            Box(Modifier.sharedBounds(screenKey("privacy"))) {
                                PrivacyRoute(
                                    container = container,
                                    onSetPin = { backStack.add(PinChangeKey(requireCurrent = false)) },
                                    onChangePin = { backStack.add(PinChangeKey(requireCurrent = true)) },
                                    onDisablePin = { backStack.add(PinDisableKey) },
                                    onWipe = { backStack.add(WipeKey()) },
                                    onBack = back,
                                    onOpenBlocked = { backStack.add(BlockedKey) },
                                    onOpenPhrase = { backStack.add(PhraseKey()) },
                                )
                            }
                        }
                        entry<PinChangeKey> { key -> PinChangeRoute(container, key.requireCurrent, back) }
                        entry<BlockedKey> { BlockedRoute(container, back) }
                        entry<ShareKey> {
                            ShareRoute(container, onDone = {
                                container.shareRequest.value = null
                                back()
                            })
                        }
                        entry<RequestsKey> {
                            RequestsRoute(container, onOpenConversation = { backStack.add(ConversationKey(it)) }, onBack = back)
                        }
                        entry<PinDisableKey> { PinDisableRoute(container, back) }
                        entry<WipeKey> { key ->
                            val context = LocalContext.current
                            val notify = rememberNotify()
                            val failed = stringResource(app.blesk.feature.security.R.string.delete_account_failed)
                            WipeScreen(
                                kind = key.kind,
                                onWipe = {
                                    scope.launch {
                                        // The account goes first: wiping the phone first would lose the key that proves it is yours.
                                        val gone = key.kind != WipeKind.DeleteAccount || container.chats.value?.deleteAccount() == true
                                        if (gone) wipeEverything(context, container) else notify(failed)
                                    }
                                },
                                onBack = back,
                            )
                        }
                        entry<PolicyKey> { PrivacyPolicyScreen(onBack = back) }
                        entry<SandboxKey> { MetaballSandbox(onBack = back) }
                        entry<UpdateKey> { UpdateRoute(container, back) }
                        entry<WhatsNewKey> {
                            val context = LocalContext.current
                            val notes = remember {
                                runCatching { context.assets.open(NOTES).bufferedReader().use { it.readText() } }.getOrDefault("")
                            }
                            WhatsNewScreen(notes = notes, onBack = back)
                        }
                        entry<LicensesKey> { LicensesScreen(onOpenLicense = { backStack.add(LicenseKey(it)) }, onBack = back) }
                        entry<LicenseKey> { key -> LicenseTextScreen(key.license, onBack = back) }
                        entry<ConversationKey> { key ->
                            ConversationRoute(
                                container = container,
                                id = key.id,
                                focusMessageId = key.focusMessageId,
                                appearance = appearance,
                                onOpenMedia = { messageId, index -> backStack.add(MediaKey(key.id, messageId, index)) },
                                onOpenGroup = { backStack.add(GroupInfoKey(it)) },
                                onOpenSpace = { backStack.add(SpaceInfoKey(it)) },
                                onVerify = { backStack.add(ChatInfoKey(key.id)) },
                                onForward = { messageIds -> backStack.add(ForwardKey(key.id, messageIds)) },
                                onCall = { video -> context.startActivity(CallActivity.call(context, key.id, video)) },
                                onBack = back,
                                onOpenPerson = { backStack.add(ChatInfoKey(it)) },
                                onOpenStickers = { backStack.add(StickersKey) },
                                onMakeSticker = { uri -> backStack.add(StickerMakeKey(uri)) },
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
                        entry<ArchiveKey> {
                            ArchiveRoute(container, onOpenConversation = { backStack.add(ConversationKey(it)) }, onBack = back)
                        }
                        entry<RoomsAddKey> {
                            RoomsAddRoute(container, onCreate = { backStack.add(NewGroupKey(NewKind.Room)) }, onBack = back)
                        }
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
                        entry<BotsKey> {
                            Box(Modifier.sharedBounds(screenKey("bots"))) { BotsRoute(container, back) }
                        }
                        entry<SafetyKey> { key -> SafetyRoute(container, key.username, back) }
                        entry<ChatInfoKey> { key ->
                            ChatInfoRoute(
                                container = container,
                                id = key.id,
                                onVerify = { backStack.add(SafetyKey(key.id)) },
                                onOpenMedia = { backStack.add(MediaKey(key.id, it)) },
                                onCall = { video -> context.startActivity(CallActivity.call(context, key.id, video)) },
                                onChatDeleted = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                                onBack = back,
                                onOpenConversation = { backStack.add(ConversationKey(it)) },
                            )
                        }
                        entry<ForwardKey> { key -> ForwardRoute(container, key.from, key.messageIds, onDone = back, onBack = back) }
                        entry<NotificationsKey> {
                            Box(Modifier.sharedBounds(screenKey("notifications"))) { NotificationsRoute(container, back) }
                        }
                        entry<StorageKey> {
                            Box(Modifier.sharedBounds(screenKey("storage"))) {
                                StorageRoute(container, onOpenOffline = { backStack.add(KeptOfflineKey) }, onBack = back)
                            }
                        }
                        entry<KeptOfflineKey> {
                            OfflineRoute(
                                container,
                                onOpen = { chat, message -> backStack.add(ConversationKey(chat, message)) },
                                onBack = back,
                            )
                        }
                        entry<SpaceInfoKey> { key ->
                            SpaceInfoRoute(container, key.handle, onLeft = {
                                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                            }, onBack = back)
                        }
                        entry<MediaKey> { key -> MediaRoute(container, key, onBack = back) }
                        entry<DevicesKey> { DevicesRoute(container, back) }
                        entry<MotionKey> { MotionRoute(container, onOpenChat = { backStack.add(ConversationKey(it)) }, onBack = back) }
                        entry<PhraseKey> { key -> PhraseRoute(container, key, onBack = back) }
                        entry<StickersKey> { StickersRoute(container, onOpen = { backStack.add(StickerSetKey(it)) }, onBack = back) }
                        entry<StickerSetKey> { key -> StickerSetRoute(container, key.setId, onMake = { backStack.add(it) }, onBack = back) }
                        entry<StickerMakeKey> { key -> StickerMakeRoute(container, key, onBack = back) }
                        entry<VoiceChannelKey> { key ->
                            val chats = container.chats.collectAsStateWithLifecycle().value ?: return@entry
                            val channel by chats.conversation(key.roomId)
                                .map { room -> room?.channels?.firstOrNull { it.id == key.channelId } }
                                .collectAsStateWithLifecycle(initialValue = null)
                            channel?.let { VoiceChannelScreen(channel = it, onBack = back) }
                        }
                        entry<AppearanceKey> {
                            Box(Modifier.sharedBounds(screenKey("appearance"))) {
                                AppearanceRoute(container, appearance, back)
                            }
                        }
                        entry<AboutKey> {
                            Box(Modifier.sharedBounds(screenKey("about"))) {
                                val checkUpdates by container.settings.checkUpdates.collectAsStateWithLifecycle(true)
                                AboutScreen(
                                    versionName = BuildConfig.VERSION_NAME,
                                    debugUnlocked = debugUnlocked,
                                    onOpenLicenses = { backStack.add(LicensesKey) },
                                    onOpenPolicy = { backStack.add(PolicyKey) },
                                    onOpenSandbox = { backStack.add(SandboxKey) },
                                    updates = UpdateControls(
                                        checking = checkUpdates,
                                        onChecking = { enabled ->
                                            scope.launch {
                                                container.settings.setCheckUpdates(enabled)
                                                if (enabled) container.updater.check()
                                            }
                                        },
                                        onOpenWhatsNew = { backStack.add(WhatsNewKey) },
                                    ),
                                    onUnlockDebug = {
                                        scope.launch { container.settings.setDebugUnlocked(true) }
                                        container.eventLog.record(EventCode.DebugUnlocked)
                                    },
                                    onBack = back,
                                )
                            }
                        }
                        entry<DebugKey> {
                            val entries by container.eventLog.entries.collectAsStateWithLifecycle()
                            var crashVersion by remember { mutableIntStateOf(0) }
                            val crash = remember(crashVersion) { container.crashStore.read() }
                            val frameMeter by container.frameMeter.collectAsStateWithLifecycle()
                            DebugScreen(
                                entries = entries.toImmutableList(),
                                crash = crash,
                                appInfo = "blesk ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), " +
                                    "Android ${Build.VERSION.SDK_INT}",
                                onClearCrash = {
                                    container.crashStore.clear()
                                    container.eventLog.record(EventCode.CrashCleared)
                                    crashVersion++
                                },
                                onClearJournal = container.eventLog::clear,
                                onBack = back,
                                onOpenMotion = { backStack.add(MotionKey) },
                                frameMeter = frameMeter,
                                onFrameMeter = { on -> container.frameMeter.value = on },
                            )
                        }
                    },
                )
                val note by container.inAppNote.collectAsStateWithLifecycle()
                InAppNotice(
                    note = note,
                    onOpen = { id ->
                        container.inAppNote.value = null
                        backStack.add(ConversationKey(id))
                    },
                    onDismiss = { container.inAppNote.value = null },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
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

/** Sends a link to your profile through any app the person chooses. */
private fun shareProfile(context: Context, username: String, key: String?) {
    if (username.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, profileLink(username, key))
    context.startActivity(Intent.createChooser(send, context.getString(app.blesk.feature.profile.R.string.my_share_title)))
}

/** Asks the server through the proxy; in the demo, where there is no server, only whether the proxy answers. */
private suspend fun checkProxy(container: AppContainer, setting: ProxySetting): ProxyCheck {
    val config = setting.copy(enabled = true).toConfig() ?: return ProxyCheck.Silent
    val api = container.api
    return when {
        api != null -> api.checkThrough(config)?.let { ProxyCheck.Works(it) } ?: ProxyCheck.Silent
        NetworkProxy.reachable(config) -> ProxyCheck.Works(null)
        else -> ProxyCheck.Silent
    }
}
