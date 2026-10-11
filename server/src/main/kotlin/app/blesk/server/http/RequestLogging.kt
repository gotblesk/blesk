package app.blesk.server.http

import io.ktor.server.application.ApplicationPlugin
import io.ktor.server.application.createApplicationPlugin
import io.ktor.server.application.hooks.CallSetup
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.httpMethod
import io.ktor.server.routing.RoutingNode
import io.ktor.server.routing.RoutingRoot
import io.ktor.util.AttributeKey
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("blesk.http")
private val StartedAt = AttributeKey<Long>("blesk.startedAt")
private val RouteTemplate = AttributeKey<String>("blesk.route")

/**
 * One line per request: method, route template, status and latency. Paths are never logged as
 * sent, because they carry usernames and attachment ids.
 */
val RequestLogging: ApplicationPlugin<Unit> = createApplicationPlugin("RequestLogging") {
    application.monitor.subscribe(RoutingRoot.RoutingCallStarted) { call ->
        call.attributes.put(RouteTemplate, call.route.template())
    }
    on(CallSetup) { call -> call.attributes.put(StartedAt, System.nanoTime()) }
    on(ResponseSent) { call ->
        // Status pages may send a second response for the same call; only the first is logged.
        val started = call.attributes.getOrNull(StartedAt) ?: return@on
        call.attributes.remove(StartedAt)
        val millis = (System.nanoTime() - started) / NANOS_PER_MILLI
        val route = call.attributes.getOrNull(RouteTemplate) ?: "unmatched"
        log.info("{} {} {} {}ms", call.request.httpMethod.value, route, call.response.status()?.value ?: 0, millis)
    }
}

/** Path segments only; method, auth and other transparent selectors render in parentheses and are skipped. */
internal fun RoutingNode.template(): String {
    val segments = generateSequence(this) { it.parent }
        .map { it.selector.toString() }
        .filter { it.isNotEmpty() && !it.startsWith("(") }
        .toList()
        .asReversed()
    return "/" + segments.joinToString("/").trim('/')
}

private const val NANOS_PER_MILLI = 1_000_000L
