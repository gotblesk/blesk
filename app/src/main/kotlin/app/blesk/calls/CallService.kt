package app.blesk.calls

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.blesk.BleskApplication
import app.blesk.core.data.calls.CallPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Keeps the microphone (and camera) working while the call runs with the screen off or the app in the background. */
class CallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications by lazy { CallNotifications(this) }
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val calls = (application as BleskApplication).container.calls.value
        val session = calls?.session?.value
        if (session == null || session.phase == CallPhase.Ended) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val types = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or
                    if (session.video && granted(Manifest.permission.CAMERA)) ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA else 0
            } else {
                0
            }
            runCatching { ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, notifications.ongoing(session), types) }
                .onFailure { stopSelf() }
            scope.launch {
                calls.session.collect { current ->
                    if (current == null || current.phase == CallPhase.Ended) {
                        stopSelf()
                    } else {
                        runCatching { notificationManager().notify(CallNotifications.ONGOING_ID, notifications.ongoing(current)) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun notificationManager() = getSystemService(android.app.NotificationManager::class.java)

    companion object {
        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) }
        }
    }
}
