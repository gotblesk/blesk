package app.blesk.navigation

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.calls.CallActivity
import app.blesk.calls.CallCapsule
import app.blesk.core.data.SpaceController
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.glass.LensAccessories
import app.blesk.core.design.glass.LocalLensAccessories
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.core.design.theme.Blesk
import app.blesk.core.network.NetworkProxy
import app.blesk.core.network.ServerAddress
import app.blesk.economy.EconomyNotices
import app.blesk.feature.chats.ContactActions
import app.blesk.feature.chats.LocalChatLocks
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.feature.onboarding.text
import app.blesk.feature.profile.MyQrScreen
import app.blesk.feature.profile.profileLink
import app.blesk.feature.security.WipeKind
import app.blesk.feature.security.WipeScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.BuildSignature
import app.blesk.feature.settings.DemoSignUp
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
import app.blesk.feature.settings.SignedBy
import app.blesk.feature.settings.TermsScreen
import app.blesk.feature.settings.UpdateControls
import app.blesk.feature.settings.WhatsNewScreen
import app.blesk.locks.ChatLockGate
import app.blesk.locks.ChatLocksRoute
import app.blesk.locks.LockCodeRoute
import app.blesk.locks.LockedDetails
import app.blesk.onboarding.NotificationAsk
import app.blesk.onboarding.OtherServerKey
import app.blesk.onboarding.ServerRef
import app.blesk.onboarding.SetupOpener
import app.blesk.onboarding.onboardingEntries
import app.blesk.onboarding.openSignUp
import app.blesk.onboarding.rememberSignUpStart
import app.blesk.onboarding.startKey
import app.blesk.shared.appearance.Appearance
import app.blesk.toConfig
import app.blesk.update.SigningKeys
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
data class GroupInfoKey(val groupId: String, val editing: Boolean = false) : NavKey

@Serializable
data class ChannelInfoKey(val id: String) : NavKey

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
data object EconomyKey : NavKey

@Serializable
data object StorageKey : NavKey

@Serializable
data object ServerKey : NavKey

@Serializable
data object InvitesKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data object ChatLocksKey : NavKey

@Serializable
data object LockCodeKey : NavKey

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
data object TermsKey : NavKey

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
    // Under the splash until the settings are read, so the stack starts with the screen the app opens with.
    val opening = container.start.collectAsStateWithLifecycle().value ?: return
    val backStack = rememberNavBackStack(startKey(opening))
    val scope = rememberCoroutineScope()
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val debugUnlocked by container.settings.debugUnlocked.collectAsStateWithLifecycle(initialValue = false)
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    val demo = mode is AppMode.Demo
    // Every invite link leads to blesk's own server, so an invite made on another one would send a friend astray.
    val openInvites: (() -> Unit)? = (mode as? AppMode.Server)
        ?.takeIf { ServerAddress(it.url, it.pin) == ServerAddress.DEFAULT }
        ?.let { { backStack.add(InvitesKey) } }
    val context = LocalContext.current
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }
    val openChannel: (Conversation, RoomChannel) -> Unit = { room, channel ->
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
    }

    // A chat asked for by a notification or by a share opens over the chats only: on the welcome screen and
    // during the setup it waits for them.
    val home = backStack.firstOrNull() == HomeKey
    val openRequest by container.openRequest.collectAsStateWithLifecycle()
    LaunchedEffect(openRequest, home) {
        if (!home) return@LaunchedEffect
        val id = openRequest ?: return@LaunchedEffect
        container.openRequest.value = null
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        backStack.add(ConversationKey(id))
    }

    val notify = rememberNotify()
    val needsServer = stringResource(app.blesk.R.string.call_needs_server)
    val personMissing = if (demo) needsServer else stringResource(app.blesk.feature.chats.R.string.contacts_not_found)
    val openDirect: (String, (String) -> Unit) -> Unit = { username, then ->
        scope.launch {
            val chats = container.chats.value ?: return@launch
            chats.openDirect(username)?.let(then) ?: notify(personMissing)
        }
    }
    val contactRemoved = stringResource(app.blesk.feature.chats.R.string.contacts_removed)
    val shareRequest by container.shareRequest.collectAsStateWithLifecycle()
    LaunchedEffect(shareRequest, home) {
        if (home && shareRequest != null && backStack.lastOrNull() != ShareKey) backStack.add(ShareKey)
    }

    LaunchedEffect(backStack.size) {
        container.eventLog.record(EventCode.ScreenOpened, backStack.size.toLong())
    }

    // Invite links lead to blesk's own server, so sign-up by one goes there whatever was chosen before.
    val openSignUp: () -> Unit = {
        container.signUp.server = ServerAddress.DEFAULT
        backStack.openSignUp(ServerRef.of(ServerAddress.DEFAULT))
    }
    val openOtherServer: (ServerAddress) -> Unit = { address ->
        while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
        // In the demo an account starts from «Сервер и прокси», where the server step comes back to.
        if (backStack.firstOrNull() == HomeKey) backStack.add(ServerKey)
        backStack.add(OtherServerKey(ServerRef.of(address)))
    }

    // Which way the stack went last, read by each screen while it moves: deeper or back.
    val direction = remember { StackDirection(backStack.size) }
    direction.update(backStack.size)

    val motion = Blesk.motion
    SharedTransitionLayout {
        var playerOpen by rememberSaveable { mutableStateOf(false) }
        val accessories = remember(container) {
            LensAccessories(top = { CallCapsule(container) }, bottom = { MusicCapsule(container, onExpand = { playerOpen = true }) })
        }
        // In v1.0 screens only stack: nothing flows from one screen into the next, so shared
        // elements are left out (LocalSharedScope stays empty) until the flows come back in P1.
        val needCodeText = stringResource(app.blesk.feature.chats.R.string.lock_need_code)
        val needCode: () -> Unit = {
            notify(needCodeText)
            backStack.add(PinChangeKey(requireCurrent = false))
        }
        val chatLocks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
        CompositionLocalProvider(LocalLensAccessories provides accessories, LocalChatLocks provides chatLocks) {
            // The in-app notice floats over every screen, under the status bar.
            Box(Modifier.fillMaxSize()) {
                val screenDecorator = remember {
                    NavEntryDecorator<Any>(decorate = { entry -> Screen(forward = { direction.forward }) { entry.Content() } })
                }
                // The screen under the open player is dimmed and softly blurred.
                val blur by animateDpAsState(if (playerOpen) PlayerBlur else 0.dp, motion.settle(), label = "playerBlur")
                NavDisplay(
                    modifier = Modifier.blur(blur),
                    sharedTransitionScope = this@SharedTransitionLayout,
                    transitionSpec = { screenEnter(motion) },
                    popTransitionSpec = { screenBack(motion) },
                    predictivePopTransitionSpec = { screenBack(motion) },
                    entryDecorators = listOf(rememberSaveableStateHolderNavEntryDecorator(), screenDecorator),
                    backStack = backStack,
                    onBack = back,
                    entryProvider = entryProvider {
                        onboardingEntries(container, backStack, direction)
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
                                onNeedCode = needCode,
                                contactActions = ContactActions(
                                    onOpen = { contact -> openDirect(contact.username) { backStack.add(ChatInfoKey(it)) } },
                                    onWrite = { contact -> openDirect(contact.username) { backStack.add(ConversationKey(it)) } },
                                    onCall = { contact ->
                                        openDirect(contact.username) { id -> context.startActivity(CallActivity.call(context, id, false)) }
                                    },
                                    onInvite = openInvites ?: (mode as? AppMode.Server)?.let { server ->
                                        { scope.launch { invite(context, server.username, container.chats.value?.linkKey()) } }
                                    },
                                    onRemove = { contact ->
                                        container.notices.undoable(contactRemoved) {
                                            container.chats.value?.removeContact(contact.username)
                                        }
                                    },
                                ),
                                onOpenRoom = { backStack.add(GroupInfoKey(it)) },
                                onOpenChannel = openChannel,
                                settings = SettingsActions(
                                    onOpenProfile = { backStack.add(ProfileKey) },
                                    onOpenAppearance = { backStack.add(AppearanceKey) },
                                    onOpenPrivacy = { backStack.add(PrivacyKey) },
                                    onOpenAbout = { backStack.add(AboutKey) },
                                    onOpenDebug = { backStack.add(DebugKey) },
                                    onOpenServer = { backStack.add(ServerKey) },
                                    onOpenBots = { backStack.add(BotsKey) },
                                    onOpenNotifications = { backStack.add(NotificationsKey) },
                                    onOpenEconomy = { backStack.add(EconomyKey) },
                                    onOpenStorage = { backStack.add(StorageKey) },
                                    onOpenUpdate = { backStack.add(UpdateKey) },
                                    onOpenPhrase = { backStack.add(PhraseKey) },
                                    onOpenDevices = { backStack.add(DevicesKey) },
                                    onOpenMusic = { backStack.add(MusicKey) },
                                    onOpenInvites = openInvites,
                                    // A profile link and its code lead to an account on a server; the demo has none to point at.
                                    onShowQr = if (demo) ({ notify(needsServer) }) else ({ backStack.add(MyQrKey) }),
                                    onDeleteAccount = if (demo) null else ({ backStack.add(WipeKey(WipeKind.DeleteAccount)) }),
                                    onShare = if (demo) {
                                        { notify(needsServer) }
                                    } else {
                                        {
                                            scope.launch {
                                                val key = container.chats.value?.linkKey()
                                                shareProfile(context, container.settings.profile.first().username, key)
                                            }
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
                        entry<ProfileKey> { ProfileRoute(container, back, onReopen = { backStack.add(ProfileKey) }) }
                        entry<MyQrKey> {
                            val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
                            val linkKey by produceState<String?>(null) { value = container.chats.value?.linkKey() }
                            MyQrScreen(profile?.username.orEmpty(), onBack = back, linkKey = linkKey)
                        }
                        entry<ServerKey> {
                            Box(Modifier.sharedBounds(screenKey("server"))) {
                                val server = mode as? AppMode.Server
                                val proxy by container.settings.proxy.collectAsStateWithLifecycle(ProxySetting())
                                // Only from this screen: a look at the server ending after the person left must not open sign-up elsewhere.
                                val start = rememberSignUpStart(container) { next ->
                                    if (backStack.lastOrNull() == ServerKey) backStack.add(next)
                                }
                                val problem = start.problem
                                ServerScreen(
                                    connected = server?.let { ServerState(it.url, it.username, it.pin != null) },
                                    onBack = {
                                        start.stop()
                                        back()
                                    },
                                    proxy = ProxyControls(
                                        setting = proxy,
                                        onSave = { scope.launch { container.settings.setProxy(it) } },
                                        onCheck = { checkProxy(container, it) },
                                    ),
                                    signUp = if (server != null) {
                                        null
                                    } else {
                                        DemoSignUp(
                                            server = container.signUp.otherServer,
                                            busy = start.busy,
                                            problem = problem?.text(),
                                            onCreate = start::begin,
                                            onOtherServer = {
                                                start.stop()
                                                backStack.add(OtherServerKey())
                                            },
                                            onUpdate = if (problem == AccessProblem.AppTooOld) ({ backStack.add(UpdateKey) }) else null,
                                        )
                                    },
                                )
                            }
                        }
                        entry<InvitesKey> {
                            Box(Modifier.sharedBounds(screenKey("invites"))) { InvitesRoute(container, back) }
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
                                    onOpenPhrase = { backStack.add(PhraseKey) },
                                    onOpenLocks = { backStack.add(ChatLocksKey) },
                                )
                            }
                        }
                        entry<PinChangeKey> { key -> PinChangeRoute(container, key.requireCurrent, onDone = back) }
                        entry<ChatLocksKey> {
                            ChatLocksRoute(container, onSeparateCode = { backStack.add(LockCodeKey) }, onNeedCode = needCode, onBack = back)
                        }
                        entry<LockCodeKey> { LockCodeRoute(container, onDone = back) }
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
                        entry<TermsKey> { TermsScreen(onBack = back) }
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
                            ChatLockGate(container, key.id) {
                                ConversationRoute(
                                    container = container,
                                    id = key.id,
                                    focusMessageId = key.focusMessageId,
                                    appearance = appearance,
                                    onOpenMedia = { messageId, index -> backStack.add(MediaKey(key.id, messageId, index)) },
                                    onOpenGroup = { backStack.add(GroupInfoKey(it)) },
                                    onOpenChannel = { backStack.add(ChannelInfoKey(it)) },
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
                                onOpenInvites = openInvites,
                                onBack = back,
                            )
                        }
                        entry<ArchiveKey> {
                            ChatLockGate(container, ChatLocks.ARCHIVE) {
                                ArchiveRoute(
                                    container,
                                    onOpenConversation = { backStack.add(ConversationKey(it)) },
                                    onOpenChannel = openChannel,
                                    onBack = back,
                                    onNeedCode = needCode,
                                )
                            }
                        }
                        entry<RoomsAddKey> {
                            RoomsAddRoute(
                                container,
                                onCreate = { backStack.add(NewGroupKey(NewKind.Room)) },
                                onOpenConversation = { id ->
                                    back()
                                    backStack.add(ConversationKey(id))
                                },
                                onBack = back,
                            )
                        }
                        entry<ScanKey> {
                            ScanRoute(
                                container,
                                onOpenConversation = { id ->
                                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    backStack.add(ConversationKey(id))
                                },
                                onSignUp = {
                                    while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                    openSignUp()
                                },
                                onOtherServer = openOtherServer,
                                onBack = back,
                            )
                        }
                        entry<NewGroupKey> { key ->
                            NewGroupRoute(container, key.kind, onCreated = { id, room ->
                                while (backStack.size > 1) backStack.removeAt(backStack.lastIndex)
                                backStack.add(ConversationKey(id))
                                // The owner of a new room starts in its details, to add channels and call people in.
                                if (room) backStack.add(GroupInfoKey(id, editing = true))
                            }, onBack = back)
                        }
                        entry<ChannelInfoKey> { key ->
                            LockedDetails(container, key.id, needCode) {
                                ChannelInfoRoute(
                                    container,
                                    key.id,
                                    onOpenRoom = { backStack.add(GroupInfoKey(it)) },
                                    onOpenMessage = { message -> backStack.add(ConversationKey(key.id, message)) },
                                    onOpenPerson = { username -> openDirect(username) { backStack.add(ChatInfoKey(it)) } },
                                    onOpenMedia = { chat, message -> backStack.add(MediaKey(chat, message)) },
                                    onBack = back,
                                )
                            }
                        }
                        entry<GroupInfoKey> { key ->
                            LockedDetails(container, key.groupId, needCode) {
                                GroupInfoRoute(
                                    container,
                                    key.groupId,
                                    editing = key.editing,
                                    onLeft = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                                    onBack = back,
                                    onOpenPerson = { username -> openDirect(username) { backStack.add(ChatInfoKey(it)) } },
                                    onOpenMedia = { chat, message -> backStack.add(MediaKey(chat, message)) },
                                    onOpenVoice = if (demo) {
                                        { channelId -> backStack.add(VoiceChannelKey(key.groupId, channelId)) }
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                        entry<BotsKey> {
                            Box(Modifier.sharedBounds(screenKey("bots"))) { BotsRoute(container, back) }
                        }
                        entry<SafetyKey> { key -> SafetyRoute(container, key.username, back) }
                        entry<ChatInfoKey> { key ->
                            LockedDetails(container, key.id, needCode) {
                                ChatInfoRoute(
                                    container = container,
                                    id = key.id,
                                    onVerify = { backStack.add(SafetyKey(key.id)) },
                                    onOpenMedia = { backStack.add(MediaKey(key.id, it)) },
                                    onCall = { video -> context.startActivity(CallActivity.call(context, key.id, video)) },
                                    onChatDeleted = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                                    onBack = back,
                                    onOpenConversation = { backStack.add(ConversationKey(it)) },
                                    onWrite = {
                                        val below = backStack.getOrNull(backStack.lastIndex - 1)
                                        if (below is ConversationKey && below.id == key.id) {
                                            back()
                                        } else {
                                            backStack.add(ConversationKey(key.id))
                                        }
                                    },
                                )
                            }
                        }
                        entry<ForwardKey> { key -> ForwardRoute(container, key.from, key.messageIds, onDone = back, onBack = back) }
                        entry<EconomyKey> { EconomyRoute(container, back) }
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
                            SpaceInfoRoute(
                                container,
                                key.handle,
                                onLeft = { while (backStack.size > 1) backStack.removeAt(backStack.lastIndex) },
                                onBack = back,
                                onOpenMedia = { chat, message -> backStack.add(MediaKey(chat, message)) },
                            )
                        }
                        entry<MediaKey> { key -> MediaRoute(container, key, onBack = back) }
                        entry<DevicesKey> { DevicesRoute(container, back) }
                        entry<MusicKey> { MusicRoute(container, back) }
                        entry<MotionKey> { MotionRoute(container, onOpenChat = { backStack.add(ConversationKey(it)) }, onBack = back) }
                        entry<PhraseKey> { PhraseRoute(container, fresh = false, onDone = back, onLater = null, onBack = back) }
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
                                val signature = remember {
                                    SigningKeys.of(context).let { BuildSignature(SignedBy.valueOf(it.origin.name), it.fingerprint) }
                                }
                                val official by container.onboarding.officialChannel.collectAsStateWithLifecycle(initialValue = null)
                                val channelMissing = stringResource(app.blesk.feature.chats.R.string.link_not_found)
                                AboutScreen(
                                    signature = signature,
                                    // Subscribes on the way in: the channel is the server's own, read by everyone on it.
                                    onOpenOfficial = official?.takeUnless { demo }?.let { handle ->
                                        {
                                            scope.launch {
                                                val spaces = container.chats.value as? SpaceController ?: return@launch
                                                val id = spaces.joinSpace(handle) ?: return@launch notify(channelMissing)
                                                backStack.add(ConversationKey(id))
                                            }
                                        }
                                    },
                                    versionName = BuildConfig.VERSION_NAME,
                                    debugUnlocked = debugUnlocked,
                                    onOpenLicenses = { backStack.add(LicensesKey) },
                                    onOpenPolicy = { backStack.add(PolicyKey) },
                                    onOpenTerms = { backStack.add(TermsKey) },
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
                EconomyNotices(container.economy, onSettings = { backStack.add(EconomyKey) })
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
                PlayerOverlay(
                    container = container,
                    open = playerOpen,
                    onClose = { playerOpen = false },
                    onShare = { chat, message ->
                        playerOpen = false
                        backStack.add(ForwardKey(chat, listOf(message)))
                    },
                )
                IncomingLinks(
                    container = container,
                    screens = LinkScreens(
                        conversation = { backStack.add(ConversationKey(it)) },
                        profile = { backStack.add(ChatInfoKey(it)) },
                        signUp = openSignUp,
                        otherServer = openOtherServer,
                    ),
                    atHome = backStack.singleOrNull() == HomeKey,
                )
                SetupOpener(container.onboarding.start, backStack, direction)
                // Over the chats and what opens from them, never over the welcome screen or the setup.
                if (home) NotificationAsk(container)
            }
        }
    }
}

/** Sends a link to your profile through any app the person chooses. */
private val PlayerBlur = 18.dp

private fun shareProfile(context: Context, username: String, key: String?) {
    if (username.isBlank()) return
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, profileLink(username, key))
    context.startActivity(Intent.createChooser(send, context.getString(app.blesk.feature.profile.R.string.my_share_title)))
}

/** Asks the server through the proxy; in the demo, where there is no server, only whether the proxy answers. */
internal suspend fun checkProxy(container: AppContainer, setting: ProxySetting): ProxyCheck {
    val config = setting.copy(enabled = true).toConfig() ?: return ProxyCheck.Silent
    val api = container.api
    return when {
        api != null -> api.checkThrough(config)?.let { ProxyCheck.Works(it) } ?: ProxyCheck.Silent
        NetworkProxy.reachable(config) -> ProxyCheck.Works(null)
        else -> ProxyCheck.Silent
    }
}

/** Remembers whether the back stack last grew (a screen opened deeper) or shrank (a screen closed). */
internal class StackDirection(private var depth: Int) {
    var forward = true
        private set

    fun update(now: Int) {
        if (now == depth) return
        forward = now > depth
        depth = now
    }

    /** The whole stack was swapped for [depth] screens: it went on, not back. */
    fun replaced(depth: Int) {
        forward = true
        this.depth = depth
    }
}

/**
 * Swaps the whole stack for [keys], played as going on, like opening a screen. The new screens come in before
 * the old ones go, in one change, so the stack is never empty.
 */
internal fun NavBackStack<NavKey>.replaceWith(keys: List<NavKey>, direction: StackDirection) = Snapshot.withMutableSnapshot {
    direction.replaced(keys.size)
    val old = size
    addAll(keys)
    repeat(old) { removeAt(0) }
}
