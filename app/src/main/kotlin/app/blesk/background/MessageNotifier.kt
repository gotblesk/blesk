package app.blesk.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.blesk.AppContainer
import app.blesk.InAppNote
import app.blesk.MainActivity
import app.blesk.R
import app.blesk.core.data.IncomingMessages
import app.blesk.core.data.model.Markup
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.NotificationPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

/** One notification per chat; how much it shows is the person's choice, names only by default. */
internal class MessageNotifier(private val context: Context, private val container: AppContainer) {
    private val manager = NotificationManagerCompat.from(context)
    private val lines = HashMap<String, ArrayDeque<String>>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(CHANNEL, context.getString(R.string.notify_channel_messages), NotificationManager.IMPORTANCE_HIGH)
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun start() {
        container.scope.launch {
            container.chats
                .flatMapLatest { (it as? IncomingMessages)?.incoming ?: emptyFlow() }
                .collect(::show)
        }
    }

    /** Called when a chat is opened: what it said is now seen. */
    fun clear(conversationId: String) {
        lines.remove(conversationId)
        manager.cancel(conversationId, ID)
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun show(note: IncomingNote) {
        val settings = container.settings.notifications.first()
        if (!settings.enabled || !manager.areNotificationsEnabled()) return
        if (container.visible && container.openConversation == note.conversationId) return
        val conversation = container.chats.value?.conversation(note.conversationId)?.first()
        if (conversation?.muted(System.currentTimeMillis()) == true) return
        val generic = context.getString(R.string.notify_new_message)
        val text = Markup.parse(note.text).masked(context.getString(R.string.notify_spoiler))
        // With the app lock on, nothing about who wrote or what shows outside the app.
        val preview = if (container.pinVault.isSet) NotificationPreview.Nothing else settings.preview
        val (title, line) = when (preview) {
            NotificationPreview.NameAndText -> note.title to (note.author?.let { "$it: $text" } ?: text)
            NotificationPreview.NameOnly -> note.title to generic
            NotificationPreview.Nothing -> context.getString(R.string.app_name) to generic
        }
        // In front, the message comes as a capsule out of the top lens rather than over the app from the system.
        if (container.visible) {
            container.inAppNote.value = InAppNote(note.conversationId, title, line)
            return
        }
        val history = lines.getOrPut(note.conversationId) { ArrayDeque() }
        history.addLast(line)
        while (history.size > MAX_LINES) history.removeFirst()
        val open = PendingIntent.getActivity(
            context,
            note.conversationId.hashCode(),
            MainActivity.open(context, note.conversationId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val hidden = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(generic)
            .build()
        val style = NotificationCompat.InboxStyle().also { inbox -> history.forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(line)
            .setStyle(style)
            .setNumber(history.size)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(hidden)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(note.conversationId, ID, notification) }
    }

    private companion object {
        const val CHANNEL = "messages"
        const val ID = 7
        const val MAX_LINES = 5
    }
}
