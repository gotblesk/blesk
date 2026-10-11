package app.blesk.server.auth

import app.blesk.server.Services
import app.blesk.shared.protocol.Api
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.bearer
import io.ktor.server.auth.parseAuthorizationHeader
import io.ktor.server.auth.principal
import io.ktor.server.request.path

const val BEARER = "bearer"

fun AuthenticationConfig.bleskBearer(services: Services) {
    bearer(BEARER) {
        authHeader { call -> call.authorizationHeader() }
        authenticate { credential -> services.auth.principal(Tokens.hash(credential.token), services.now()) }
    }
}

/** The authenticated account; only valid inside routes wrapped in `authenticate(BEARER)`. */
val ApplicationCall.account: AccountPrincipal
    get() = checkNotNull(principal<AccountPrincipal>()) { "route is not authenticated" }

/** Browsers and some socket libraries cannot set headers on a WebSocket handshake, so the socket also accepts `?token=`. */
private fun ApplicationCall.authorizationHeader(): HttpAuthHeader? {
    val header = runCatching { request.parseAuthorizationHeader() }.getOrNull()
    if (header != null) return header
    if (request.path() != Api.SOCKET) return null
    val token = request.queryParameters["token"]?.takeIf { it.isNotEmpty() } ?: return null
    return HttpAuthHeader.Single("Bearer", token)
}
