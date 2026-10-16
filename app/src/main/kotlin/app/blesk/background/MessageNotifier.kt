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
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.IncomingNote
import app.blesk.core.data.settings.LockNotify
import app.blesk.core.data.settings.NoteKind
import app.blesk.core.data.settings.NotificationPreview
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.locks.lockOf
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
            val system = context.getSystemService(NotificationManager::class.java)
            // One channel per kind, so the system settings can silence groups while keeping direct chats loud.
            val channels = NoteKind.entries.map { kind ->
                val importance =
                    if (kind == NoteKind.Channel) NotificationManager.IMPORTANCE_DEFAULT else NotificationManager.IMPORTANCE_HIGH
                NotificationChannel(channelOf(kind), context.getString(channelName(kind)), importance)
            }
            system.deleteNotificationChannel(OLD_CHANNEL)
            system.createNotificationChannels(channels)
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

    /** Whether [text] names you with @username; the text itself never leaves this check. */
    private suspend fun mentions(text: String): Boolean {
        val me = container.settings.profile.first().username
        return me.isNotBlank() && Regex("(^|[^\\w])@" + Regex.escape(me) + "\\b", RegexOption.IGNORE_CASE).containsMatchIn(text)
    }

    /** What the lock over a chat says about its notifications; a chat in a locked archive follows the archive. */
    private suspend fun lockNotifyOf(conversationId: String, archived: Boolean): LockNotify? {
        val locks = container.chatLocks.locks.first()
        val lockId = locks.lockOf(conversationId) ?: ChatLocks.ARCHIVE.takeIf { archived && locks.isLocked(it) }
        return lockId?.let { locks.rulesFor(it).notify }
    }

    private suspend fun wanted(
        note: IncomingNote,
        settings: NotificationSettings,
        muted: Boolean,
        lockNotify: LockNotify?,
    ): Boolean = when {
        !settings.enabled || !manager.areNotificationsEnabled() -> false
        container.visible && container.openConversation == note.conversationId -> false
        muted || lockNotify == LockNotify.None -> false
        note.conversationId in settings.mentionsOnly -> mentions(note.text)
        else -> true
    }

    @android.annotation.SuppressLint("MissingPermission")
    private suspend fun show(note: IncomingNote) {
        val settings = container.settings.notifications.first()
        val conversation = container.chats.value?.conversation(note.conversationId)?.first()
        val lockNotify = lockNotifyOf(note.conversationId, conversation?.archived == true)
        if (!wanted(note, settings, conversation?.muted(System.currentTimeMillis()) == true, lockNotify)) return
        val generic = context.getString(R.string.notify_new_message)
        val text = Markup.parse(note.text).masked(context.getString(R.string.notify_spoiler))
        // With the app lock on, or for a chat under a lock that says so, nothing about who wrote or what shows outside the app.
        val preview = when {
            container.pinVault.isSet || lockNotify == LockNotify.Anonymous -> NotificationPreview.Nothing
            else -> settings.preview
        }
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
        val channel = channelOf(note.kind)
        val hidden = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(generic)
            .build()
        val style = NotificationCompat.InboxStyle().also { inbox -> history.forEach(inbox::addLine) }
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notify)
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
        const val OLD_CHANNEL = "messages"

        fun channelOf(kind: NoteKind) = "messages_" + kind.name.lowercase()

        fun channelName(kind: NoteKind) = when (kind) {
            NoteKind.Direct -> R.string.notify_channel_direct
            NoteKind.Group -> R.string.notify_channel_groups
            NoteKind.Room -> R.string.notify_channel_rooms
            NoteKind.Channel -> R.string.notify_channel_channels
        }

        const val ID = 7
        const val MAX_LINES = 5
    }
}
