package app.blesk.server.invites

import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.clientAddress
import app.blesk.server.http.conflict
import app.blesk.server.http.ensure
import app.blesk.server.http.orFail
import app.blesk.server.http.rateLimited
import app.blesk.shared.protocol.Api
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post

/** The signed-in person's invites; every request here carries a person's token. */
fun Route.inviteRoutes(services: Services) {
    get(Api.INVITES) {
        call.respond(services.invites.list(call.account.id, services.now()))
    }

    post(Api.INVITES) {
        val owner = call.account
        // An invite leads to a registration, so making one draws on the same per-address budget.
        ensure(services.authRateLimiter.tryAcquire(call.clientAddress(services.trustProxy))) { rateLimited(services.authRateLimiter) }
        val invite = services.invites.create(owner.id, services.now()).orFail { conflict(ErrorCodes.INVITE_LIMIT) }
        call.respond(HttpStatusCode.Created, invite)
    }
}
