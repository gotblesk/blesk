package app.blesk.core.data.settings

/** How much a message notification shows; the default keeps text off the lock screen and the status bar. */
enum class NotificationPreview { NameAndText, NameOnly, Nothing }

data class NotificationSettings(
    val enabled: Boolean = true,
    val preview: NotificationPreview = NotificationPreview.NameOnly,
    /** Keeps the connection open with a permanent notification, so messages and calls arrive with the app closed. */
    val alwaysOnline: Boolean = false,
    /** Chats, such as room channels, that notify only when someone writes your @username. */
    val mentionsOnly: Set<String> = emptySet(),
)

/** Where a message came from; each kind has its own notification channel. */
enum class NoteKind { Direct, Group, Room, Channel }

/** A new message worth telling about; [text] is never logged. */
data class IncomingNote(
    val conversationId: String,
    val title: String,
    val author: String?,
    val text: String,
    val kind: NoteKind = NoteKind.Direct,
)
