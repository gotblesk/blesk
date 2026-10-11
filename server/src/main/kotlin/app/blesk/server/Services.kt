package app.blesk.server

import app.blesk.server.attachments.AttachmentRepository
import app.blesk.server.attachments.AttachmentStorage
import app.blesk.server.auth.AccountRepository
import app.blesk.server.auth.AuthRepository
import app.blesk.server.db.Database
import app.blesk.server.http.RateLimiter
import app.blesk.server.keys.KeyRepository
import app.blesk.server.messages.MessageRepository
import app.blesk.server.socket.SocketHub
import java.time.Clock
import java.time.Instant

/** Everything the routes need, wired once at startup. */
class Services(
    database: Database,
    val storage: AttachmentStorage,
    val authRateLimiter: RateLimiter,
    val trustProxy: Boolean,
    private val clock: Clock = Clock.systemUTC(),
) {
    val accounts = AccountRepository(database)
    val auth = AuthRepository(database)
    val keys = KeyRepository(database)
    val messages = MessageRepository(database)
    val attachments = AttachmentRepository(database)
    val hub = SocketHub()

    fun now(): Instant = clock.instant()
}
