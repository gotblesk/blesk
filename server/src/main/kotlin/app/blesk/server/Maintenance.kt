package app.blesk.server

import app.blesk.server.attachments.chunkName
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.toJavaDuration

private val log = LoggerFactory.getLogger("blesk.maintenance")

/** Attachments and undelivered messages are kept this long, then deleted. */
private val RetentionPeriod = 30.days.toJavaDuration()

/** A chunked upload never finished in a day is not coming back. */
private val AbandonedUpload = 1.days.toJavaDuration()

/** Long enough to cover any client retry of the same message. */
private val IdempotencyWindow = 7.days.toJavaDuration()

/** Acknowledged bot updates are kept a day so a person's retry is still recognised; unread ones a week. */
private val ConsumedUpdateRetention = 1.days.toJavaDuration()
private val PendingUpdateRetention = 7.days.toJavaDuration()

private const val ATTACHMENT_BATCH = 500

fun CoroutineScope.launchMaintenance(services: Services, interval: Duration): Job = launch {
    while (isActive) {
        try {
            runMaintenance(services)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (
            // A failed round must not end the loop; the next one retries whatever is left.
            @Suppress("TooGenericExceptionCaught") failure: Exception,
        ) {
            log.warn("Maintenance failed: {}", failure.javaClass.name)
        }
        delay(interval)
    }
}

suspend fun runMaintenance(services: Services) {
    val now = services.now()
    services.auth.deleteExpired(now)
    services.messages.deleteOlderThan(queuedBefore = now - RetentionPeriod, sentBefore = now - IdempotencyWindow)
    services.bots.deleteOlderThan(
        consumedBefore = now - ConsumedUpdateRetention,
        pendingBefore = now - PendingUpdateRetention,
        messagesBefore = now - RetentionPeriod,
    )
    services.authRateLimiter.evictIdle()
    services.botRateLimiter.evictIdle()
    services.botApiRateLimiter.evictIdle()
    services.postRateLimiter.evictIdle()
    services.callRateLimiter.evictIdle()
    do {
        val expired = services.attachments.expired(now - RetentionPeriod, now - AbandonedUpload, ATTACHMENT_BATCH)
        expired.forEach { (id, chunks) ->
            withContext(Dispatchers.IO) {
                if (chunks == 0) services.storage.delete(id) else repeat(chunks) { services.storage.delete(chunkName(id, it)) }
            }
            services.attachments.forget(id)
        }
    } while (expired.size == ATTACHMENT_BATCH)
}
