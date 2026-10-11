package app.blesk.server

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

/** Long enough to cover any client retry of the same message. */
private val IdempotencyWindow = 7.days.toJavaDuration()

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
    services.authRateLimiter.evictIdle()
    services.postRateLimiter.evictIdle()
    services.callRateLimiter.evictIdle()
    do {
        val expired = services.attachments.expired(now - RetentionPeriod, ATTACHMENT_BATCH)
        expired.forEach { id ->
            withContext(Dispatchers.IO) { services.storage.delete(id) }
            services.attachments.forget(id)
        }
    } while (expired.size == ATTACHMENT_BATCH)
}
