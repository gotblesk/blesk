package app.blesk.server.bots

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration

/**
 * Wakes long-polling getUpdates calls when a bot gets something new. In memory, like the socket hub:
 * a poll that misses a signal still sees the update on its next database read.
 */
class BotSignals {
    private val versions = ConcurrentHashMap<Long, MutableStateFlow<Long>>()

    /** Read before querying the database, then pass to [awaitAfter], so nothing slips in between. */
    fun version(botId: Long): Long = flow(botId).value

    fun signal(botId: Long) {
        flow(botId).update { it + 1 }
    }

    /** True when something arrived after [version], false when [timeout] passed first. */
    suspend fun awaitAfter(botId: Long, version: Long, timeout: Duration): Boolean =
        withTimeoutOrNull(timeout) { flow(botId).first { it != version } } != null

    fun forget(botId: Long) {
        versions.remove(botId)
    }

    private fun flow(botId: Long) = versions.computeIfAbsent(botId) { MutableStateFlow(0L) }
}
