package app.blesk.calls

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.blesk.R
import app.blesk.core.data.calls.CallSession

/** The two call notifications: ringing (full screen when locked) and the ongoing call. */
internal class CallNotifications(private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val system = context.getSystemService(NotificationManager::class.java)
            val name = context.getString(R.string.calls_channel_incoming)
            val ringing = NotificationChannel(INCOMING, name, NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                enableVibration(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val ongoing =
                NotificationChannel(ONGOING, context.getString(R.string.calls_channel_ongoing), NotificationManager.IMPORTANCE_LOW)
            system.createNotificationChannels(listOf(ringing, ongoing))
        }
    }

    fun incoming(session: CallSession): Notification {
        val person = Person.Builder().setName(session.title).setImportant(true).build()
        val screen = activity(CallActivity.screen(context), REQUEST_SCREEN)
        val answer = activity(CallActivity.answer(context), REQUEST_ANSWER)
        val decline = PendingIntent.getBroadcast(
            context,
            REQUEST_DECLINE,
            Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.DECLINE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, INCOMING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer).setIsVideo(session.video))
            .setContentIntent(screen)
            .setFullScreenIntent(screen, true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .build()
            .apply { flags = flags or Notification.FLAG_INSISTENT }
    }

    fun ongoing(session: CallSession): Notification {
        val person = Person.Builder().setName(session.title).build()
        val hangUp = PendingIntent.getBroadcast(
            context,
            REQUEST_HANG_UP,
            Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.HANG_UP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, ONGOING)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp).setIsVideo(session.video))
            .setContentIntent(activity(CallActivity.screen(context), REQUEST_SCREEN))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setUsesChronometer(session.startedAtMillis != null)
            .setWhen(session.startedAtMillis ?: System.currentTimeMillis())
            .build()
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun showIncoming(session: CallSession) {
        if (manager.areNotificationsEnabled()) runCatching { manager.notify(INCOMING_ID, incoming(session)) }
    }

    fun cancelIncoming() = manager.cancel(INCOMING_ID)

    private fun activity(intent: Intent, request: Int) =
        PendingIntent.getActivity(context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    companion object {
        const val INCOMING = "calls_incoming"
        const val ONGOING = "calls_ongoing"
        const val INCOMING_ID = 41
        const val ONGOING_ID = 42
        private const val REQUEST_SCREEN = 1
        private const val REQUEST_ANSWER = 2
        private const val REQUEST_DECLINE = 3
        private const val REQUEST_HANG_UP = 4
    }
}
