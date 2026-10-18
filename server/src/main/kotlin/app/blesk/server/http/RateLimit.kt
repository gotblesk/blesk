package app.blesk.server.http

import app.blesk.server.RateLimitConfig
import app.blesk.shared.protocol.ErrorBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RouteSelector
import io.ktor.server.routing.RouteSelectorEvaluation
import io.ktor.server.routing.RoutingResolveContext
import java.util.concurrent.ConcurrentHashMap

/** Token bucket per key (a client address, a person). Lives in memory: a restart simply refills everyone. */
class RateLimiter(
    private val capacity: Int,
    refillPerMinute: Double,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    constructor(config: RateLimitConfig, nanoTime: () -> Long = System::nanoTime) :
        this(config.capacity, config.refillPerMinute.toDouble(), nanoTime)

    private class Bucket(var tokens: Double, var updatedAt: Long)

    private val buckets = ConcurrentHashMap<String, Bucket>()
    private val refillPerNano = refillPerMinute / NANOS_PER_MINUTE

    fun tryAcquire(key: String): Boolean {
        val now = nanoTime()
        val bucket = buckets.computeIfAbsent(key) { Bucket(capacity.toDouble(), now) }
        return synchronized(bucket) {
            bucket.tokens = minOf(capacity.toDouble(), bucket.tokens + (now - bucket.updatedAt) * refillPerNano)
            bucket.updatedAt = now
            if (bucket.tokens >= 1) {
                bucket.tokens -= 1
                true
            } else {
                false
            }
        }
    }

    /** Drops buckets that have refilled completely, so the map does not grow with every address ever seen. */
    fun evictIdle() {
        val now = nanoTime()
        buckets.entries.removeIf { (_, bucket) ->
            synchronized(bucket) { bucket.tokens + (now - bucket.updatedAt) * refillPerNano >= capacity }
        }
    }

    private companion object {
        const val NANOS_PER_MINUTE = 60_000_000_000.0
    }
}

fun ApplicationCall.clientAddress(trustProxy: Boolean): String {
    if (trustProxy) {
        request.headers["X-Forwarded-For"]?.substringBefore(',')?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return request.origin.remoteAddress
}

private class RateLimitSettings {
    lateinit var limiter: RateLimiter
    var trustProxy = false
}

private val RateLimit = createRouteScopedPlugin("RateLimit", ::RateLimitSettings) {
    val limiter = pluginConfig.limiter
    val trustProxy = pluginConfig.trustProxy
    onCall { call ->
        if (!limiter.tryAcquire(call.clientAddress(trustProxy))) {
            call.respond(HttpStatusCode.TooManyRequests, ErrorBody(ErrorCodes.RATE_LIMITED))
        }
    }
}

/** Wraps [build] so every route inside answers 429 once the caller's bucket is empty. */
fun Route.rateLimited(limiter: RateLimiter, trustProxy: Boolean, build: Route.() -> Unit): Route {
    val limited = createChild(RateLimitedSelector)
    limited.install(RateLimit) {
        this.limiter = limiter
        this.trustProxy = trustProxy
    }
    limited.build()
    return limited
}

private object RateLimitedSelector : RouteSelector() {
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int) = RouteSelectorEvaluation.Transparent

    override fun toString() = "(rate limited)"
}
