package app.blesk.server.auth

import app.blesk.server.Services
import app.blesk.server.http.ApiException
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.decodeBase64
import app.blesk.server.http.encodeBase64
import app.blesk.server.http.ensure
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.Discovery
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import app.blesk.shared.protocol.Usernames
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val ChallengeLifetime = 2.minutes.toJavaDuration()
private val TokenLifetime = 30.days.toJavaDuration()

fun Route.registrationRoutes(services: Services) {
    post(Api.ACCOUNTS) {
        val request = call.receiveJson<RegisterRequest>()
        ensure(Usernames.isValid(request.username)) { badRequest(ErrorCodes.INVALID_USERNAME) }
        // Names ending like a bot's belong to bots, so a person can never pass for a program or the other way round.
        ensure(!request.username.endsWith(Bots.SUFFIX)) { badRequest(ErrorCodes.USERNAME_RESERVED) }
        val keyBytes = request.identityKey.decodeBase64(ErrorCodes.INVALID_KEY)
        val identityKey = Signatures.identityKey(keyBytes).orFail { badRequest(ErrorCodes.INVALID_KEY) }
        val signature = request.signature.decodeBase64(ErrorCodes.INVALID_SIGNATURE)
        ensure(Signatures.verify(identityKey, Api.registrationProof(request.username), signature)) {
            badRequest(ErrorCodes.INVALID_SIGNATURE)
        }
        val created = services.accounts.create(request.username, keyBytes, request.registrationId, services.now())
        ensure(created) { ApiException(HttpStatusCode.Conflict, ErrorCodes.USERNAME_TAKEN) }
        call.respond(HttpStatusCode.Created, AccountInfo(request.username, request.identityKey))
    }
}

fun Route.loginRoutes(services: Services) {
    post(Api.CHALLENGE) {
        val request = call.receiveJson<ChallengeRequest>()
        ensure(Usernames.isValid(request.username)) { badRequest(ErrorCodes.INVALID_USERNAME) }
        // Issued whether or not the account exists, so this endpoint cannot be used to probe usernames.
        val nonce = Tokens.newNonce()
        val expiresAt = services.now() + ChallengeLifetime
        services.auth.saveChallenge(nonce, request.username, expiresAt)
        call.respond(Challenge(nonce, expiresAt.toEpochMilli()))
    }

    post(Api.TOKEN) {
        val request = call.receiveJson<TokenRequest>()
        val now = services.now()
        val authFailed = { ApiException(HttpStatusCode.Unauthorized, ErrorCodes.AUTH_FAILED) }
        val expiresAt = services.auth.consumeChallenge(request.nonce, request.username).orFail(authFailed)
        ensure(expiresAt > now, authFailed)
        val account = services.accounts.find(request.username).orFail(authFailed)
        val identityKey = Signatures.identityKey(account.identityKey).orFail(authFailed)
        val signature = request.signature.decodeBase64(ErrorCodes.INVALID_SIGNATURE)
        ensure(Signatures.verify(identityKey, Api.authProof(request.nonce), signature), authFailed)

        val token = Tokens.newToken()
        val tokenExpiresAt = now + TokenLifetime
        services.auth.saveToken(Tokens.hash(token), account.id, tokenExpiresAt)
        call.respond(TokenResponse(token, tokenExpiresAt.toEpochMilli()))
    }
}

fun Route.accountLookupRoutes(services: Services) {
    get("${Api.ACCOUNTS}/{username}") {
        val username = call.parameters["username"].orEmpty()
        ensure(Usernames.isValid(username)) { notFound() }
        val account = services.accounts.find(username).orFail { notFound() }
        // A hidden account answers exactly like a missing one, so a search learns nothing.
        ensure(account.visibleTo(call.account.id, call.request.queryParameters[Api.LINK])) { notFound() }
        call.respond(AccountInfo(account.username, account.identityKey.encodeBase64()))
    }

    delete(Api.ACCOUNT) {
        services.accounts.delete(call.account.id)
        call.respond(HttpStatusCode.NoContent)
    }

    put(Api.DISCOVERY) {
        val request = call.receiveJson<Discovery>()
        services.accounts.setDiscoverable(call.account.id, request.discoverable)
        call.respond(request)
    }
}
