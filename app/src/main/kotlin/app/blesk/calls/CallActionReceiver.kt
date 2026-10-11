package app.blesk.calls

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.blesk.BleskApplication

/** Decline and hang up from the notification, without opening the app. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val calls = (context.applicationContext as BleskApplication).container.calls.value ?: return
        when (intent.action) {
            DECLINE -> calls.decline()
            HANG_UP -> calls.hangUp()
        }
    }

    companion object {
        const val DECLINE = "app.blesk.call.DECLINE"
        const val HANG_UP = "app.blesk.call.HANG_UP"
    }
}
