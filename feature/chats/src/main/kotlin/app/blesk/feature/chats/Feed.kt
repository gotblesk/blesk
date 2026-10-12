package app.blesk.feature.chats

import app.blesk.core.data.model.Message
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import java.time.ZoneId

/** What the conversation list is made of, top to bottom. */
internal sealed interface FeedEntry {
    val key: String

    data class Day(val date: LocalDate) : FeedEntry {
        override val key = "day:$date"
    }

    data object Unread : FeedEntry {
        override val key = "unread"
    }

    /** Consecutive messages of one author, drawn as one column. */
    data class Run(val messages: ImmutableList<Message>) : FeedEntry {
        override val key: String = messages.first().id
    }
}

/**
 * Splits messages into runs and puts a date before each new day and the unread divider before
 * [unreadFrom]. A run never crosses a day or the divider.
 */
internal fun buildFeed(messages: List<Message>, zone: ZoneId, unreadFrom: String? = null): List<FeedEntry> {
    val feed = ArrayList<FeedEntry>()
    var current = ArrayList<Message>()
    var day: LocalDate? = null
    fun close() {
        if (current.isNotEmpty()) feed += FeedEntry.Run(current.toImmutableList())
        current = ArrayList()
    }
    messages.forEach { message ->
        val date = message.sentAt.atZone(zone).toLocalDate()
        val previous = current.lastOrNull()
        val newDay = date != day
        val unread = message.id == unreadFrom
        val sameRun = previous != null &&
            !newDay &&
            !unread &&
            previous.authorId == message.authorId &&
            message.sentAt.epochSecond - previous.sentAt.epochSecond < GROUP_GAP_SECONDS
        if (!sameRun) close()
        if (newDay) {
            feed += FeedEntry.Day(date)
            day = date
        }
        if (unread) feed += FeedEntry.Unread
        current += message
    }
    close()
    return feed
}

private const val GROUP_GAP_SECONDS = 10 * 60
