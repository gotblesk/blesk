package app.blesk.server

import app.blesk.server.attachments.attachmentRoutes
import app.blesk.server.auth.BEARER
import app.blesk.server.auth.accountLookupRoutes
import app.blesk.server.auth.bleskBearer
import app.blesk.server.auth.loginRoutes
import app.blesk.server.auth.registrationRoutes
import app.blesk.server.auth.usernameCheckRoutes
import app.blesk.server.bots.BOT_BEARER
import app.blesk.server.bots.botApiRoutes
import app.blesk.server.bots.botBearer
import app.blesk.server.bots.botRoutes
import app.blesk.server.calls.callRoutes
import app.blesk.server.http.ApiException
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.RequestLogging
import app.blesk.server.http.rateLimited
import app.blesk.server.invites.inviteRoutes
import app.blesk.server.keys.keyRoutes
import app.blesk.server.messages.messageRoutes
import app.blesk.server.socket.socketRoutes
import app.blesk.server.spaces.spaceRoutes
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.ServerInfo
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("blesk.server")

fun Application.bleskModule(services: Services, maintenanceInterval: Duration = 15.minutes) {
    install(RequestLogging)
    install(ContentNegotiation) { json(BleskJson) }
    install(WebSockets) {
        pingPeriod = 30.seconds
        timeout = 60.seconds
        maxFrameSize = Api.MAX_MESSAGE_BYTES * 2L
    }
    install(Authentication) {
        bleskBearer(services)
        botBearer(services)
    }
    install(StatusPages) {
        exception<ApiException> { call, failure ->
            failure.retryAfterSeconds?.let { call.response.header(HttpHeaders.RetryAfter, it.toString()) }
            call.respond(failure.status, ErrorBody(failure.code, failure.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ -> call.respond(HttpStatusCode.BadRequest, ErrorBody(ErrorCodes.BAD_REQUEST)) }
        exception<Throwable> { call, failure ->
            // Only the class name: messages of database errors can quote the values involved.
            log.error("Unhandled {}", failure.javaClass.name)
            call.respond(HttpStatusCode.InternalServerError, ErrorBody(ErrorCodes.INTERNAL))
        }
        status(HttpStatusCode.Unauthorized) { call, status -> call.respond(status, ErrorBody(ErrorCodes.UNAUTHORIZED)) }
        status(HttpStatusCode.NotFound) { call, status -> call.respond(status, ErrorBody(ErrorCodes.NOT_FOUND)) }
    }

    routing {
        // For probes and uptime monitors, so outside the rate limits: the check itself runs one at a time.
        get(Api.HEALTH) {
            if (services.health.isUp()) {
                call.respond(Health("ok"))
            } else {
                call.respond(HttpStatusCode.ServiceUnavailable, Health("unavailable"))
            }
        }
        rateLimited(services.requestRateLimiter, services.trustProxy) {
            get(Api.INFO) {
                val info = ServerInfo(
                    calls = services.callTokens != null,
                    invites = services.registration == RegistrationMode.Invite,
                    minClientVersion = Api.MIN_CLIENT_VERSION,
                    officialChannel = services.spaces.officialHandle(),
                )
                call.respond(info)
            }
            authenticate(BEARER, optional = true) {
                usernameCheckRoutes(services)
            }
            rateLimited(services.authRateLimiter, services.trustProxy) {
                registrationRoutes(services)
                loginRoutes(services)
            }
            authenticate(BEARER) {
                accountLookupRoutes(services)
                keyRoutes(services)
                messageRoutes(services)
                attachmentRoutes(services)
                socketRoutes(services)
                spaceRoutes(services)
                callRoutes(services)
                botRoutes(services)
                inviteRoutes(services)
            }
            authenticate(BOT_BEARER) {
                botApiRoutes(services)
            }
        }
    }

    launchMaintenance(services, maintenanceInterval)
}

@Serializable
private data class Health(val status: String)
