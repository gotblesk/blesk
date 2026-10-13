package app.blesk.calls

import android.Manifest
import android.app.KeyguardManager
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.compose.CameraXViewfinder
import androidx.camera.viewfinder.core.ImplementationMode
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.blesk.BleskApplication
import app.blesk.R
import app.blesk.core.calls.CallManager
import app.blesk.core.calls.CallMedia
import app.blesk.core.calls.MediaState
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.NoticeHost
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.LocalHapticLevel
import app.blesk.feature.calls.CallActions
import app.blesk.feature.calls.CallScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.toHapticLevel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/** The call screen; it can show over the lock screen so a call can be answered right away. */
class CallActivity : ComponentActivity() {
    private val container by lazy { (application as BleskApplication).container }

    /** An action waiting for the permission dialog. */
    private var pending: Intent? = null
    private var waiting by mutableStateOf(false)

    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val intent = pending ?: return@registerForActivityResult
            pending = null
            waiting = false
            if (result[Manifest.permission.RECORD_AUDIO] == true || granted(Manifest.permission.RECORD_AUDIO)) {
                perform(intent)
            } else {
                container.notices.show(getString(R.string.calls_need_microphone))
                if (intent.action == ANSWER) container.calls.value?.decline()
                finishIfIdle()
            }
        }

    private val screenCapture = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@registerForActivityResult
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val media = container.calls.value?.media as? CallMedia ?: return@registerForActivityResult
        lifecycleScope.launch { media.shareScreen(data) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handle(intent)
        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
            LaunchedEffect(privacy.secureScreen) {
                if (privacy.secureScreen) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            val manager by container.calls.collectAsStateWithLifecycle()
            val vibration by container.settings.vibration.collectAsStateWithLifecycle(initialValue = VibrationLevel.Normal)
            CompositionLocalProvider(
                LocalHapticLevel provides vibration.toHapticLevel(),
                LocalNotices provides container.notices,
            ) {
                BleskTheme(appearance = appearance) {
                    Box(Modifier.fillMaxSize()) {
                        manager?.let { Call(it) }
                        NoticeHost(container.notices, Modifier.align(Alignment.BottomCenter), lift = 160.dp)
                    }
                }
            }
        }
    }

    /** A video call goes into a corner window when you leave the app, so the faces stay in view. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val session = container.calls.value?.session?.value ?: return
        if (session.video && session.phase == CallPhase.Active) {
            runCatching { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(CORNER_W, CORNER_H)).build()) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Composable
    private fun Call(manager: CallManager) {
        val session by manager.session.collectAsStateWithLifecycle()
        val media by manager.media.state.collectAsStateWithLifecycle()
        val scope = rememberCoroutineScope()
        val current = session
        LaunchedEffect(current?.phase, waiting) {
            when {
                current == null -> if (!waiting) finish()
                current.phase == CallPhase.Calling || current.phase == CallPhase.Connecting || current.phase == CallPhase.Active ->
                    CallService.start(this@CallActivity)
                else -> Unit
            }
        }
        current ?: return
        val livekit = manager.media as? CallMedia
        // The demo has no room to talk through, so its microphone, speaker and camera are switches of its own.
        LaunchedEffect(current.id) {
            container.demoCallMedia.value = MediaState(micOn = true, cameraOn = current.video, speakerOn = current.video)
        }
        val demoMedia by container.demoCallMedia.collectAsStateWithLifecycle()
        val offline = getString(R.string.call_needs_server)
        CallScreen(
            session = current,
            media = if (manager.demo) demoMedia else media,
            room = livekit?.currentRoom,
            nameOf = { id -> container.chats.value?.contact(id)?.name ?: id },
            demo = manager.demo,
            demoPreview = { DemoCamera() },
            actions = CallActions(
                accept = { request(answer(this)) },
                decline = manager::decline,
                hangUp = manager::hangUp,
                toggleMic = {
                    if (manager.demo) {
                        container.demoCallMedia.value = demoMedia.copy(micOn = !demoMedia.micOn)
                    } else {
                        scope.launch { livekit?.setMic(!media.micOn) }
                    }
                },
                toggleCamera = {
                    when {
                        !granted(Manifest.permission.CAMERA) -> permissions.launch(arrayOf(Manifest.permission.CAMERA))
                        manager.demo -> container.demoCallMedia.value = demoMedia.copy(cameraOn = !demoMedia.cameraOn)
                        else -> scope.launch { livekit?.setCamera(!media.cameraOn) }
                    }
                },
                flipCamera = { if (manager.demo) container.circleRecorder.flip() else livekit?.flipCamera() },
                toggleSpeaker = {
                    if (manager.demo) {
                        container.demoCallMedia.value = demoMedia.copy(speakerOn = !demoMedia.speakerOn)
                    } else {
                        livekit?.setSpeaker(!media.speakerOn)
                    }
                },
                // Sharing the screen needs someone to share it with; the demo says so instead of doing nothing.
                toggleShare = when {
                    manager.demo -> ({ container.notices.show(offline) })
                    livekit == null -> null
                    else -> ({ toggleShare(livekit, media) })
                },
            ),
        )
    }

    /** The demo has no one on the other end, so the call shows your own camera. */
    @Composable
    private fun DemoCamera() {
        val owner = LocalLifecycleOwner.current
        val recorder = container.circleRecorder
        val request by (if (granted(Manifest.permission.CAMERA)) recorder.preview else flowOf(null)).collectAsStateWithLifecycle(null)
        DisposableEffect(owner) {
            lifecycleScope.launch { if (granted(Manifest.permission.CAMERA)) recorder.open(owner) }
            onDispose { recorder.close() }
        }
        request?.let {
            CameraXViewfinder(surfaceRequest = it, implementationMode = ImplementationMode.EMBEDDED, modifier = Modifier.fillMaxSize())
        }
    }

    private fun toggleShare(media: CallMedia, state: MediaState) {
        if (state.sharing) {
            lifecycleScope.launch { media.shareScreen(null) }
        } else {
            val projection = getSystemService(MediaProjectionManager::class.java)
            screenCapture.launch(projection.createScreenCaptureIntent())
        }
    }

    private fun handle(intent: Intent?) {
        when (intent?.action) {
            CALL, VOICE, ANSWER -> request(intent)
            else -> Unit
        }
    }

    /** Asks for the microphone (and the camera for video) before acting. */
    private fun request(intent: Intent) {
        val video = intent.getBooleanExtra(EXTRA_VIDEO, false) ||
            (intent.action == ANSWER && container.calls.value?.session?.value?.video == true)
        val needed = buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (video) add(Manifest.permission.CAMERA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.filterNot(::granted)
        if (needed.isEmpty()) {
            perform(intent)
        } else {
            pending = intent
            waiting = true
            permissions.launch(needed.toTypedArray())
        }
    }

    private fun perform(intent: Intent) {
        val manager = container.calls.value ?: return
        when (intent.action) {
            ANSWER -> manager.accept()
            CALL -> {
                val to = intent.getStringExtra(EXTRA_TO) ?: return
                if (!manager.call(to, intent.getBooleanExtra(EXTRA_VIDEO, false))) {
                    container.notices.show(getString(R.string.calls_busy))
                }
            }
            VOICE -> manager.joinVoice(
                groupId = intent.getStringExtra(EXTRA_GROUP) ?: return,
                channelId = intent.getStringExtra(EXTRA_CHANNEL) ?: return,
                conversationId = intent.getStringExtra(EXTRA_CONVERSATION) ?: return,
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
            )
        }
        // Handled once; a rotation must not ring again.
        setIntent(Intent(this, CallActivity::class.java))
    }

    private fun finishIfIdle() {
        if (container.calls.value?.session?.value == null) finish()
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        getSystemService(KeyguardManager::class.java)?.let { keyguard ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, null)
        }
    }

    companion object {
        private const val CALL = "app.blesk.call.CALL"
        private const val VOICE = "app.blesk.call.VOICE"
        private const val ANSWER = "app.blesk.call.ANSWER"
        private const val EXTRA_TO = "to"
        private const val EXTRA_VIDEO = "video"
        private const val EXTRA_GROUP = "group"
        private const val EXTRA_CHANNEL = "channel"
        private const val EXTRA_CONVERSATION = "conversation"
        private const val EXTRA_TITLE = "title"

        fun screen(context: Context) = Intent(context, CallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        fun answer(context: Context) = screen(context).setAction(ANSWER)

        fun call(context: Context, username: String, video: Boolean) =
            screen(context).setAction(CALL).putExtra(EXTRA_TO, username).putExtra(EXTRA_VIDEO, video)

        fun voice(context: Context, groupId: String, channelId: String, conversationId: String, title: String) =
            screen(context).setAction(VOICE)
                .putExtra(EXTRA_GROUP, groupId)
                .putExtra(EXTRA_CHANNEL, channelId)
                .putExtra(EXTRA_CONVERSATION, conversationId)
                .putExtra(EXTRA_TITLE, title)
    }
}

private const val CORNER_W = 9
private const val CORNER_H = 16
