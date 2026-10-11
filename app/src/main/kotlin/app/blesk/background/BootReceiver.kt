package app.blesk.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.blesk.BleskApplication
import app.blesk.core.data.settings.AppMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** After a restart, "always online" comes back on its own. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as BleskApplication).container
        val pending = goAsync()
        container.scope.launch {
            try {
                val server = container.settings.mode.first() is AppMode.Server
                if (server && container.settings.notifications.first().alwaysOnline) KeepAliveService.start(context)
            } finally {
                pending.finish()
            }
        }
    }
}
