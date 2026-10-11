package app.blesk

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.core.data.settings.LockDelay
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.theme.BleskTheme
import app.blesk.navigation.BleskNavigation
import app.blesk.navigation.LockRoute
import app.blesk.shared.appearance.Appearance

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BleskApplication).container
        lifecycle.addObserver(
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> container.lock.onBackground()
                    Lifecycle.Event.ON_START -> container.lock.onForeground(lockDelay)
                    else -> Unit
                }
            },
        )
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
            BleskTheme(appearance = appearance) {
                Box(Modifier.fillMaxSize()) {
                    BleskNavigation(container)
                    if (container.lock.locked) LockRoute(container, biometric = privacy.biometric)
                }
            }
        }
    }

    private var lockDelay = LockDelay.Immediately.millis

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
