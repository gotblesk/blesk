package app.blesk.server.calls

import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.http.ApiException
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.ensure
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.CallTokenRequest
import app.blesk.shared.protocol.Calls
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

fun Route.callRoutes(services: Services) {
    post(Calls.TOKEN) {
        val tokens = services.callTokens.orFail { ApiException(HttpStatusCode.ServiceUnavailable, ErrorCodes.CALLS_UNAVAILABLE) }
        val account = call.account
        ensure(services.callRateLimiter.tryAcquire(account.id.toString())) {
            ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED)
        }
        val request = call.receiveJson<CallTokenRequest>()
        ensure(Calls.isValidRoom(request.room)) { badRequest(ErrorCodes.INVALID_ROOM) }
        call.respond(tokens.issue(identity = account.username, room = request.room, now = services.now()))
    }
}
