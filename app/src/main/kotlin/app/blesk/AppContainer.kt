package app.blesk

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashStore
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.PrivateImage
import app.blesk.core.data.settings.SettingsStore
import app.blesk.core.media.AudioPlayer
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.MediaVault
import app.blesk.core.media.VideoCompressor
import app.blesk.core.media.VoiceRecorder
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.network.ServerAddress
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant

/** Process-wide dependencies, built once in [BleskApplication]. */
class AppContainer(context: Context) {
    val eventLog = EventLog()

    /** Background work never takes the app down; a failure is noted in the debug journal instead. */
    val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main.immediate + CoroutineExceptionHandler { _, _ -> eventLog.record(EventCode.BackgroundFailure) },
    )
    val crashStore = CrashStore(File(context.noBackupFilesDir, "diagnostics"))
    val settings = SettingsStore(context)
    val wallpaper = PrivateImage(context, "wallpaper.jpg", maxSide = 1600)
    val profilePhoto = PrivateImage(context, "avatar.jpg", maxSide = 512)
    val pinVault = PinVault(context)
    val lock = AppLock(pinVault)
    val media = MediaVault(context)
    val voiceRecorder = VoiceRecorder(context, media)
    val circleRecorder = CircleRecorder(context, media)
    val audioPlayer: AudioPlayer by lazy { AudioPlayer(context, scope) }

    @androidx.annotation.OptIn(UnstableApi::class)
    val videoCompressor = VideoCompressor(context)
    val demo: DemoRepository by lazy {
        DemoRepository(DemoSeed.from(context.resources, Instant.now()), context.resources::attachmentLabel, scope, eventLog)
    }

    private val appContext = context.applicationContext
    private val chatsState = MutableStateFlow<ChatRepository?>(null)
    private val privacy = settings.privacy.stateIn(scope, SharingStarted.Eagerly, Privacy())
    private val profile = settings.profile
        .map { it.name to it.bio }
        .stateIn(scope, SharingStarted.Eagerly, "" to "")

    /** The world the screens show: the demo, or the server once connected. Null while opening. */
    val chats: StateFlow<ChatRepository?> = chatsState.asStateFlow()

    init {
        scope.launch {
            when (val mode = settings.mode.first()) {
                AppMode.Demo -> chatsState.value = demo
                is AppMode.Server -> chatsState.value = withContext(Dispatchers.IO) {
                    ServerStack.from(appContext, this@AppContainer, mode).repository(mode.username, privacy, profile)
                }.also { it.start() }
            }
        }
    }

    /** Creates an account on [address] and switches the app to it. */
    suspend fun connect(address: ServerAddress, username: String): RegisterResult {
        val stack = ServerStack(appContext, this, address)
        val result = withContext(Dispatchers.IO) { stack.register(username) }
        if (result != RegisterResult.Ok) return result
        val mode = AppMode.Server(address.url, address.pin, username)
        settings.setServer(mode)
        eventLog.record(EventCode.ServerRegistered)
        chatsState.value = withContext(Dispatchers.IO) { stack.repository(username, privacy, profile) }.also { it.start() }
        return result
    }
}
