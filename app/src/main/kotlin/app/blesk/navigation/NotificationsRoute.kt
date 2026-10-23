package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.background.KeepAliveService
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.feature.settings.NotificationsScreen
import app.blesk.onboarding.notificationsBlocked
import app.blesk.onboarding.openNotificationSettings
import kotlinx.coroutines.launch

@Composable
internal fun NotificationsRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settings.notifications.collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val server = mode is AppMode.Server
    // Read again on each return: the person may have allowed them in Android's settings meanwhile.
    var blocked by remember { mutableStateOf(notificationsBlocked(context)) }
    LifecycleResumeEffect(Unit) {
        blocked = notificationsBlocked(context)
        onPauseOrDispose {}
    }
    NotificationsScreen(
        settings = settings,
        serverConnected = server,
        onChange = { next ->
            scope.launch { container.settings.updateNotifications { next } }
            if (next.alwaysOnline && !settings.alwaysOnline) container.needNotifications()
            if (server && next.alwaysOnline) KeepAliveService.start(context) else KeepAliveService.stop(context)
        },
        onBack = onBack,
        blocked = blocked,
        // Android's own question may no longer come after two refusals, and below version 13 there is none.
        onAllow = { openNotificationSettings(context) },
    )
}
