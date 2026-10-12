package app.blesk

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
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
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.blesk.background.KeepAliveService
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.LockDelay
import app.blesk.core.data.settings.Privacy
import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.LocalHapticLevel
import app.blesk.navigation.BleskNavigation
import app.blesk.navigation.LockRoute
import app.blesk.shared.appearance.Appearance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
            CompositionLocalProvider(LocalHapticLevel provides vibration.toHapticLevel()) {
                BleskTheme(appearance = appearance) {
                    Box(Modifier.fillMaxSize()) {
                        BleskNavigation(container)
                        if (container.lock.locked) LockRoute(container, biometric = privacy.biometric)
                    }
                }
            }
        }
    }

    private var lockDelay = LockDelay.Immediately.millis

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_CONVERSATION) ?: return
        (application as BleskApplication).container.openRequest.value = id
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
