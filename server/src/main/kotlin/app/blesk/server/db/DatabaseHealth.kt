package app.blesk.server.db

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Whether the database answers, for `GET /health`. That endpoint is public and not rate limited, so
 * however often it is asked one check runs at a time, and the answer never waits longer than
 * [timeout]: a blocked JDBC call cannot be cancelled, only stopped being waited for.
 */
class DatabaseHealth(private val database: Database, private val timeout: Duration = 2.seconds) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var running: Deferred<Boolean>? = null

    suspend fun isUp(): Boolean {
        val check = synchronized(lock) {
            running?.takeIf { it.isActive } ?: scope.async { ping() }.also { running = it }
        }
        return withTimeoutOrNull(timeout) { check.await() } ?: false
    }

    private fun ping(): Boolean = runCatching { database.ping(timeout.inWholeSeconds.toInt().coerceAtLeast(1)) }.getOrDefault(false)
}
