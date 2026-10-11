package app.blesk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.core.design.theme.BleskTheme
import app.blesk.navigation.BleskNavigation
import app.blesk.shared.appearance.Appearance

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as BleskApplication).container
        setContent {
            val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
            SystemBars(isDark = appearance.theme.isDark)
            BleskTheme(appearance = appearance) {
                BleskNavigation(container)
            }
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
