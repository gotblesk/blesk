package app.blesk

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.blesk.background.networkAvailable
import app.blesk.core.calls.CallManager
import app.blesk.core.calls.CallMedia
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.calls.CallSignaling
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashStore
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.AutoDownload
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.PrivateImage
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.data.settings.SettingsStore
import app.blesk.core.design.component.Notices
import app.blesk.core.media.AudioPlayer
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.MediaVault
import app.blesk.core.media.VideoCompressor
import app.blesk.core.media.VoiceRecorder
import app.blesk.core.messaging.OwnProfile
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.network.BleskApi
import app.blesk.core.network.NetworkProxy
import app.blesk.core.network.ProxyConfig
import app.blesk.core.network.ProxyKind
import app.blesk.core.network.ServerAddress
import app.blesk.update.Updater
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.util.concurrent.TimeUnit

/** Process-wide dependencies, built once in [BleskApplication]. */
class AppContainer(context: Context) {
    val eventLog = EventLog()

    /** Background work never takes the app down; a failure is noted in the debug journal instead. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> eventLog.record(EventCode.BackgroundFailure) },
    )
    val crashStore = CrashStore(File(context.noBackupFilesDir, "diagnostics"))
    val settings = SettingsStore(context)

    /** Newer builds from GitHub Releases; only release builds ask, so tests never reach GitHub. */
    val updater = Updater(context)
    val wallpaper = PrivateImage(context, "wallpaper.jpg", maxSide = 1600)
    val profilePhoto = PrivateImage(context, "avatar.jpg", maxSide = 512)
    val headerPhoto = PrivateImage(context, "header.jpg", maxSide = 1600)
    val pinVault = PinVault(context)
    val lock = AppLock(pinVault)
    val media = MediaVault(context)
    val voiceRecorder = VoiceRecorder(context, media)
    val circleRecorder = CircleRecorder(context, media)
    val audioPlayer: AudioPlayer by lazy { AudioPlayer(context, scope) }
    val notices = Notices(scope)
    val network: StateFlow<Boolean> by lazy { networkAvailable(context.applicationContext, scope) }

    @androidx.annotation.OptIn(UnstableApi::class)
    val videoCompressor = VideoCompressor(context)
    val demo: DemoRepository by lazy {
        DemoRepository(DemoSeed.from(context.resources, Instant.now()), context.resources::attachmentLabel, scope, eventLog)
    }

    private val appContext = context.applicationContext
    private val chatsState = MutableStateFlow<ChatRepository?>(null)
    private val privacy = settings.privacy.stateIn(scope, SharingStarted.Eagerly, Privacy())
    private val keepArchived = settings.keepArchived.stateIn(scope, SharingStarted.Eagerly, false)

    /** «Автозагрузка», read on every arriving file. */
    val autoDownload = settings.autoDownload.stateIn(scope, SharingStarted.Eagerly, AutoDownload())
    private val profile = settings.profile
        .map { OwnProfile(it.name, it.bio, it.links, profilePhoto.stored) }
        .stateIn(scope, SharingStarted.Eagerly, OwnProfile("", ""))

    /** The world the screens show: the demo, or the server once connected. Null while opening. */
    val chats: StateFlow<ChatRepository?> = chatsState.asStateFlow()

    /** The chat on screen right now, so its messages do not also ring as notifications. */
    @Volatile
    var openConversation: String? = null

    /** A chat to open, asked for by a notification. */
    val openRequest = MutableStateFlow<String?>(null)

    /** A `blesk://` link opened from another app: someone's profile or a group invite. */
    val linkRequest = MutableStateFlow<String?>(null)

    /** Something shared into blesk from another app, waiting for the chats to send it to. */
    val shareRequest = MutableStateFlow<Shared?>(null)

    /** A message for another chat while the app is in front: shown as a capsule instead of a system notification. */
    val inAppNote = MutableStateFlow<InAppNote?>(null)

    /** Whether one of the app's screens is in front of the user; «в сети» follows it. */
    @Volatile
    var visible = false
        set(value) {
            field = value
            chats.value?.setForeground(value)
        }

    private val callsState = MutableStateFlow<CallManager?>(null)
    private var callsScope: CoroutineScope? = null

    /** Calls for the current world; in the demo they connect to nobody. */
    val calls: StateFlow<CallManager?> = callsState.asStateFlow()

    init {
        scope.launch {
            // Before the first connection, so nothing leaves directly when a proxy is set.
            NetworkProxy.set(settings.proxy.first().toConfig())
            launch { settings.proxy.collect { NetworkProxy.set(it.toConfig()) } }
            if (!BuildConfig.DEBUG && settings.checkUpdates.first()) launch { updater.check() }
            // A changed name, bio, links, photo or «Кто видит фото» goes again to the people you talk to.
            launch {
                combine(settings.profile, settings.privacy.map { it.photo }) { own, audience -> own.copy(header = "") to audience }
                    .distinctUntilChanged()
                    .drop(1)
                    .collectLatest {
                        // Typing a name saves on every change; only the settled profile goes out.
                        delay(PROFILE_SETTLE_MILLIS)
                        chats.value?.profileChanged()
                    }
            }
            when (val mode = settings.mode.first()) {
                AppMode.Demo -> show(demo, api = null)
                is AppMode.Server -> {
                    val stack = ServerStack.from(appContext, this@AppContainer, mode)
                    val repository = withContext(Dispatchers.IO) {
                        stack.repository(mode.username, privacy, profile, keepArchived)
                    }
                    repository.start()
                    show(repository, stack.api)
                }
            }
        }
    }

    /** «Хранить медиа»: files older than [days] leave the phone; the messages stay. */
    suspend fun clearOldMedia(repository: ChatRepository, days: Int) {
        if (days > 0) repository.clearMedia(null, System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days.toLong()))
    }

    /** Creates an account on [address] and switches the app to it. */
    suspend fun connect(address: ServerAddress, username: String): RegisterResult {
        val stack = ServerStack(appContext, this, address)
        val result = withContext(Dispatchers.IO) { stack.register(username) }
        if (result != RegisterResult.Ok) return result
        val mode = AppMode.Server(address.url, address.pin, username)
        settings.setServer(mode)
        eventLog.record(EventCode.ServerRegistered)
        val repository = withContext(Dispatchers.IO) { stack.repository(username, privacy, profile, keepArchived) }
        repository.start()
        show(repository, stack.api)
        return result
    }

    /** The server of the current world; null in the demo. */
    var api: BleskApi? = null
        private set

    private fun show(repository: ChatRepository, api: BleskApi?) {
        this.api = api
        repository.setForeground(visible)
        scope.launch { clearOldMedia(repository, settings.keepMediaDays.first()) }
        callsScope?.cancel()
        callsState.value?.media?.disconnect()
        val callScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        callsScope = callScope
        callsState.value = CallManager(
            scope = callScope,
            media = CallMedia(appContext, callScope),
            signaling = repository as? CallSignaling,
            http = { api?.okHttpClient() },
            nameOf = { username -> repository.contact(username)?.name ?: username },
        )
        chatsState.value = repository
    }
}

/** What the in-app capsule shows; the text is already masked to what the notification settings allow. */
data class InAppNote(val conversationId: String, val title: String, val line: String)

/** What another app shared: text (or a link) and files; the files are readable while the share lasts. */
data class Shared(val text: String?, val files: List<android.net.Uri>)

internal fun ProxySetting.toConfig(): ProxyConfig? =
    if (usable) ProxyConfig(if (socks) ProxyKind.Socks5 else ProxyKind.Http, host, port) else null

private const val PROFILE_SETTLE_MILLIS = 2_000L
