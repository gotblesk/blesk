package app.blesk

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.blesk.background.KeepAliveService
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.LockDelay
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.component.IncognitoKeyboard
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.NoticeHost
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskThemeWithWave
import app.blesk.core.motion.LocalHapticLevel
import app.blesk.debug.FrameMeter
import app.blesk.feature.chats.media.LocalPictureInPicture
import app.blesk.feature.chats.media.PictureInPicture
import app.blesk.navigation.BleskNavigation
import app.blesk.navigation.LockRoute
import app.blesk.shared.appearance.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Before the first frame: the keyboard and the lenses animate only in an edge-to-edge window.
        enableEdgeToEdge()
        val container = (application as BleskApplication).container
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> container.lock.onBackground()
                    Lifecycle.Event.ON_START -> {
                        container.lock.onForeground(lockDelay)
                        keepOnline(container)
                    }
                    else -> Unit
                }
            },
        )
        handle(intent)
        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
            LaunchedEffect(privacy) {
                lockDelay = privacy.lockDelay.millis
                if (privacy.secureScreen) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
            SystemBars(isDark = appearance.theme.isDark)
            AskForNotifications(container)
            val vibration by container.settings.vibration.collectAsStateWithLifecycle(initialValue = VibrationLevel.Normal)
            CompositionLocalProvider(
                LocalHapticLevel provides vibration.toHapticLevel(),
                LocalNotices provides container.notices,
                LocalPictureInPicture provides pictureInPicture,
            ) {
                BleskThemeWithWave(appearance = appearance) {
                    // The window under every screen is the theme's own background: no dark bands in a back gesture.
                    val windowBackground = Blesk.colors.background
                    LaunchedEffect(windowBackground) { window.setBackgroundDrawable(ColorDrawable(windowBackground.toArgb())) }
                    Box(Modifier.fillMaxSize()) {
                        IncognitoKeyboard(privacy.incognitoKeyboard) { BleskNavigation(container) }
                        NoticeHost(container.notices, Modifier.align(Alignment.BottomCenter))
                        val frameMeter by container.frameMeter.collectAsStateWithLifecycle()
                        if (frameMeter) FrameMeter(window, Modifier.align(Alignment.TopCenter))
                        if (container.lock.locked) LockRoute(container, biometric = privacy.biometric, wipeAfterTen = privacy.wipeAfterTen)
                    }
                }
            }
        }
    }

    private var lockDelay = LockDelay.Immediately.millis

    private val inCorner = mutableStateOf(false)
    private var cornerAspect: Rational? = null

    /**
     * A playing video shrinks into a corner when the person leaves the app. Not with the app
     * lock on: the corner window would show the chat past the lock.
     */
    private val pictureInPicture = PictureInPicture(inCorner) { aspect ->
        val locked = (application as BleskApplication).container.pinVault.isSet
        cornerAspect = aspect.takeIf { !locked }
        runCatching { setPictureInPictureParams(cornerParams()) }
    }

    private fun cornerParams(): PictureInPictureParams = PictureInPictureParams.Builder().apply {
        cornerAspect?.let(::setAspectRatio)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(cornerAspect != null)
    }.build()

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12 and later go into the corner by themselves, from the parameters above.
        if (cornerAspect != null && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            runCatching { enterPictureInPictureMode(cornerParams()) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        inCorner.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        // With the app lock on, the app switcher shows a blank card instead of the last screen.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setRecentsScreenshotEnabled(!(application as BleskApplication).container.pinVault.isSet)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        val container = (application as BleskApplication).container
        when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let { container.linkRequest.value = it }
            Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> shared(intent)?.let { container.shareRequest.value = it }
            else -> intent.getStringExtra(EXTRA_CONVERSATION)?.let { container.openRequest.value = it }
        }
    }

    private fun shared(intent: Intent): Shared? {
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        val files = when (intent.action) {
            Intent.ACTION_SEND_MULTIPLE -> IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
            else -> listOfNotNull(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java))
        }
        return if (text == null && files.isEmpty()) null else Shared(text, files)
    }

    /** "Always online" can only be switched on while the app is in front, so it is checked on every start. */
    private fun keepOnline(container: AppContainer) {
        lifecycleScope.launch {
            val server = container.settings.mode.first() is AppMode.Server
            if (server && container.settings.notifications.first().alwaysOnline) {
                KeepAliveService.start(this@MainActivity)
            } else {
                KeepAliveService.stop(this@MainActivity)
            }
        }
    }

    companion object {
        private const val EXTRA_CONVERSATION = "conversation"

        fun open(context: Context, conversationId: String?): Intent =
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .apply { conversationId?.let { putExtra(EXTRA_CONVERSATION, it) } }
    }

    /** With a real server, messages and calls need notifications; asked once the server is connected. */
    @Composable
    private fun AskForNotifications(container: AppContainer) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
        val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(mode is AppMode.Server) {
            val granted = ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (mode is AppMode.Server && !granted) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    @Composable
    private fun SystemBars(isDark: Boolean) {
        LaunchedEffect(isDark) {
            val style = if (isDark) {
                SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
            } else {
                SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
            }
            enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        }
    }
}
