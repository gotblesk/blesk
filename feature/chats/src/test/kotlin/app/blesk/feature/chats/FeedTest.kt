package app.blesk.feature.chats

import app.blesk.core.data.model.Message
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class FeedTest {

    private fun message(id: String, author: String, at: String) =
        Message(id = id, conversationId = "c", authorId = author, text = id, sentAt = Instant.parse(at))

    @Test
    fun datesSplitRunsAndUnreadSitsBeforeItsMessage() {
        val messages = listOf(
            message("a", "anya", "2026-10-10T20:00:00Z"),
            message("b", "anya", "2026-10-11T08:00:00Z"),
            message("c", "anya", "2026-10-11T08:01:00Z"),
            message("d", "anya", "2026-10-11T08:02:00Z"),
        )
        val feed = buildFeed(messages, ZoneOffset.UTC, unreadFrom = "d")
        assertEquals(
            listOf("day:2026-10-10", "a", "day:2026-10-11", "b", "unread", "d"),
            feed.map { it.key },
        )
        assertEquals(listOf("b", "c"), (feed[3] as FeedEntry.Run).messages.map { it.id })
    }

    @Test
    fun authorsAndPausesStartNewRuns() {
        val messages = listOf(
            message("a", "anya", "2026-10-11T08:00:00Z"),
            message("b", "me", "2026-10-11T08:01:00Z"),
            message("c", "me", "2026-10-11T09:00:00Z"),
        )
        assertEquals(listOf("day:2026-10-11", "a", "b", "c"), buildFeed(messages, ZoneOffset.UTC).map { it.key })
    }
}
