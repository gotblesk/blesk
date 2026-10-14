package app.blesk.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.blesk.BleskApplication
import app.blesk.MainActivity
import app.blesk.R
import app.blesk.core.data.Transfer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Keeps files moving when the app goes to the background: while anything is being sent or
 * received, a notification shows how far along it is, and the service ends once all is done.
 */
class TransferService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL, getString(R.string.notify_channel_transfers), NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val type =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        val started = runCatching { ServiceCompat.startForeground(this, ID, notification(emptyMap()), type) }.isSuccess
        if (!started) {
            stopSelf()
            return START_NOT_STICKY
        }
        val container = (application as BleskApplication).container
        scope.launch {
            container.chats.flatMapLatest { it?.transfers ?: flowOf(emptyMap()) }.collect { transfers ->
                val active = transfers.filterValues { !it.failed }
                if (active.isEmpty()) {
                    stopSelf()
                } else {
                    runCatching { NotificationManagerCompat.from(this@TransferService).notify(ID, notification(active)) }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(active: Map<String, Transfer>): Notification {
        val done = active.values.sumOf { it.done }
        val total = active.values.sumOf { it.total.coerceAtLeast(0) }
        val percent = if (total > 0) (done * PERCENT / total).toInt() else 0
        val text = if (total > 0) {
            getString(
                R.string.notify_transfers_text,
                Formatter.formatShortFileSize(this, done),
                Formatter.formatShortFileSize(this, total),
            )
        } else {
            null
        }
        val open = PendingIntent.getActivity(this, 0, MainActivity.open(this, null), PendingIntent.FLAG_IMMUTABLE)
        val count = active.size.coerceAtLeast(1)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(resources.getQuantityString(R.plurals.notify_transfers_title, count, count))
            .setContentText(text)
            .setProgress(PERCENT, percent, total <= 0)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val CHANNEL = "transfers"
        private const val ID = 44
        private const val PERCENT = 100

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, TransferService::class.java)) }
        }
    }
}
