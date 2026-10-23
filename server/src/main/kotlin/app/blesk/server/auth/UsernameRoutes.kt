package app.blesk.server.auth

import app.blesk.server.Services
import app.blesk.server.http.clientAddress
import app.blesk.server.http.ensure
import app.blesk.server.http.rateLimited
import app.blesk.shared.protocol.Api
import io.ktor.server.auth.principal
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * The name check, inside `authenticate(BEARER, optional = true)`. Its budgets are its own, so typing
 * a name never uses up the attempts to register with it. Someone signed in spends their own budget,
 * so many people behind one address do not run out together.
 */
fun Route.usernameCheckRoutes(services: Services) {
    get("${Api.USERNAMES}/{name}") {
        val caller = call.principal<AccountPrincipal>()
        val limiter = if (caller != null) services.usernameCheckBudget else services.usernameCheckLimiter
        val key = caller?.id?.toString() ?: call.clientAddress(services.trustProxy)
        ensure(limiter.tryAcquire(key)) { rateLimited(limiter) }
        call.respond(services.usernameChecks.check(call.parameters["name"].orEmpty()))
    }
}
