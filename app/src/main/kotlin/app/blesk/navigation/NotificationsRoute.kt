package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.background.KeepAliveService
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.feature.settings.NotificationsScreen
import kotlinx.coroutines.launch

@Composable
internal fun NotificationsRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.notifications.collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val server = mode is AppMode.Server
    NotificationsScreen(
        settings = settings,
        serverConnected = server,
        onChange = { next ->
            scope.launch { container.settings.updateNotifications { next } }
            if (server && next.alwaysOnline) KeepAliveService.start(context) else KeepAliveService.stop(context)
        },
        onBack = onBack,
    )
}
