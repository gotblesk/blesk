package app.blesk

import android.content.Context
import androidx.media3.common.util.UnstableApi
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashStore
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.PrivateImage
import app.blesk.core.data.settings.SettingsStore
import app.blesk.core.media.AudioPlayer
import app.blesk.core.media.CircleRecorder
import app.blesk.core.media.MediaVault
import app.blesk.core.media.VideoCompressor
import app.blesk.core.media.VoiceRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Instant

/** Process-wide dependencies, built once in [BleskApplication]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val eventLog = EventLog()
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
}
