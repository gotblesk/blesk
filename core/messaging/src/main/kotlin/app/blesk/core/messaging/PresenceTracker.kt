package app.blesk.core.messaging

import app.blesk.core.data.model.Presence
import app.blesk.core.data.settings.Audience
import app.blesk.shared.protocol.Body
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * «В сети» without the server knowing: while the app is in front, the people allowed to see it
 * hear so from this phone, end to end, every few minutes; their own status arrives the same way.
 */
internal class PresenceTracker(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val audience: () -> Audience,
    private val peers: suspend () -> List<String>,
    private val allowed: suspend (username: String, audience: Audience) -> Boolean,
    private val send: suspend (to: String, body: Body) -> Unit,
) {
    private val state = MutableStateFlow<Map<String, Presence>>(emptyMap())
    val presence: StateFlow<Map<String, Presence>> = state.asStateFlow()

    @Volatile
    private var foreground = false
    private var heartbeat: Job? = null

    fun setForeground(foreground: Boolean) {
        if (this.foreground == foreground) return
        this.foreground = foreground
        heartbeat?.cancel()
        heartbeat = scope.launch {
            if (!foreground) {
                announce(online = false)
                return@launch
            }
            // Online fades on the other side unless repeated, since a phone that loses the network cannot say goodbye.
            while (isActive) {
                announce(online = true)
                delay(HEARTBEAT_MILLIS)
            }
        }
    }

    /** After a reconnection the others may have marked you away; say again that you are here. */
    suspend fun reconnected() {
        if (foreground) announce(online = true)
    }

    suspend fun receive(sender: String, body: Body.Presence) {
        val heard = Instant.ofEpochMilli(clock())
        state.update { it + (sender to Presence(body.online, body.lastSeenMillis?.let(Instant::ofEpochMilli), heard)) }
        // Someone just came online: they learn about you too, if they may.
        if (body.online && !body.reply && foreground) announce(online = true, only = sender, reply = true)
    }

    /** Tells the people allowed to see it whether you are online: your recent direct chats, or just [only]. */
    private suspend fun announce(online: Boolean, only: String? = null, reply: Boolean = false) {
        val audience = audience()
        if (audience == Audience.Nobody) return
        val body = Body.Presence(online, if (online) null else clock(), reply)
        (only?.let(::listOf) ?: peers()).filter { allowed(it, audience) }.forEach { send(it, body) }
    }

    companion object {
        const val PEERS = 30
        private const val HEARTBEAT_MILLIS = 4 * 60_000L
    }
}
