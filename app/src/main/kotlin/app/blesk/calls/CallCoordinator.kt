package app.blesk.calls

import android.content.Context
import app.blesk.AppContainer
import app.blesk.core.data.calls.CallPhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** Turns call state into what Android shows: the ringing screen, notifications, the foreground service. */
internal class CallCoordinator(private val context: Context, private val container: AppContainer) {
    private val notifications = CallNotifications(context)
    private val telecom by lazy { TelecomBridge(context, container.scope) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        container.scope.launch {
            container.calls.flatMapLatest { manager ->
                manager?.session ?: emptyFlow()
            }.collect { session ->
                val manager = container.calls.value ?: return@collect
                if (session == null || session.phase == CallPhase.Ended) {
                    notifications.cancelIncoming()
                    return@collect
                }
                if (!manager.demo) telecom.track(manager, session)
                when (session.phase) {
                    CallPhase.Ringing -> {
                        notifications.showIncoming(session)
                        // In front of the user, the call screen opens at once; otherwise the notification wakes the screen.
                        if (container.visible) context.startActivity(CallActivity.screen(context))
                    }
                    else -> {
                        notifications.cancelIncoming()
                        if (container.visible) CallService.start(context)
                    }
                }
            }
        }
    }
}
