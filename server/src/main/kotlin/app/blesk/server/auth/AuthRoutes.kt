package app.blesk.server.auth

import app.blesk.server.RegistrationMode
import app.blesk.server.Services
import app.blesk.server.http.ApiException
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.conflict
import app.blesk.server.http.decodeBase64
import app.blesk.server.http.encodeBase64
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.rateLimited
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Challenge
import app.blesk.shared.protocol.ChallengeRequest
import app.blesk.shared.protocol.Discovery
import app.blesk.shared.protocol.Invites
import app.blesk.shared.protocol.RecoverRequest
import app.blesk.shared.protocol.RecoveryHash
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.TokenResponse
import app.blesk.shared.protocol.UsernameProblem
import app.blesk.shared.protocol.Usernames
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import java.security.MessageDigest
import java.util.Locale
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration

private val ChallengeLifetime = 2.minutes.toJavaDuration()
private val TokenLifetime = 30.days.toJavaDuration()

fun Route.registrationRoutes(services: Services) {
    post(Api.ACCOUNTS) {
        val request = call.receiveJson<RegisterRequest>()
        val canonical = request.username.lowercase(Locale.ROOT)
        // «Egor» when «egor» exists is that name: taken, as the check said while it was typed.
        if (canonical != request.username && Usernames.isWellFormed(canonical)) {
            ensure(services.accounts.find(canonical) == null) { conflict(ErrorCodes.USERNAME_TAKEN) }
        }
        val problem = Usernames.problem(request.username)
        // Names ending like a bot's belong to bots, so a person can never pass for a program or the other way round.
        ensure(problem != UsernameProblem.BotName) { badRequest(ErrorCodes.USERNAME_RESERVED) }
        // The service's names get this code too: released apps read only it as «a name you cannot take».
        ensure(problem == null) { badRequest(ErrorCodes.INVALID_USERNAME) }
        val keyBytes = request.identityKey.decodeBase64(ErrorCodes.INVALID_KEY)
        val identityKey = Signatures.identityKey(keyBytes).orFail { badRequest(ErrorCodes.INVALID_KEY) }
        val signature = request.signature.decodeBase64(ErrorCodes.INVALID_SIGNATURE)
        ensure(Signatures.verify(identityKey, Api.registrationProof(request.username), signature)) {
            badRequest(ErrorCodes.INVALID_SIGNATURE)
        }
        val recoveryHash = request.recoveryHash?.decodeBase64(ErrorCodes.BAD_REQUEST)
        ensure(recoveryHash == null || recoveryHash.size == RECOVERY_TOKEN_BYTES) { badRequest(ErrorCodes.BAD_REQUEST) }
        val invite = if (services.registration == RegistrationMode.Invite) inviteCode(request.invite) else null
        val now = services.now()
        when (services.accounts.create(request.username, keyBytes, request.registrationId, now, recoveryHash, invite)) {
            Registration.Created -> call.respond(HttpStatusCode.Created, AccountInfo(request.username, request.identityKey))
            Registration.UsernameTaken -> throw conflict(ErrorCodes.USERNAME_TAKEN)
            Registration.InviteInvalid -> throw forbidden(ErrorCodes.INVITE_INVALID)
        }
    }

    post(Api.RECOVER) {
        val request = call.receiveJson<RecoverRequest>()
        val authFailed = { ApiException(HttpStatusCode.Unauthorized, ErrorCodes.AUTH_FAILED) }
        val token = request.token.decodeBase64(ErrorCodes.AUTH_FAILED)
        ensure(token.size == RECOVERY_TOKEN_BYTES, authFailed)
        val keyBytes = request.identityKey.decodeBase64(ErrorCodes.INVALID_KEY)
        val identityKey = Signatures.identityKey(keyBytes).orFail { badRequest(ErrorCodes.INVALID_KEY) }
        val signature = request.signature.decodeBase64(ErrorCodes.INVALID_SIGNATURE)
        ensure(Signatures.verify(identityKey, Api.recoveryProof(request.token), signature)) { badRequest(ErrorCodes.INVALID_SIGNATURE) }
        val account = services.accounts.recover(sha256(token), keyBytes, request.registrationId).orFail(authFailed)
        // The old phone loses its connection now and its tokens are gone: it learns it was replaced.
        services.hub.disconnect(account.id)
        call.respond(AccountInfo(account.username, request.identityKey))
    }
}

/** The code of the invite a registration brings, on a server that lets people in by invite only. */
private fun inviteCode(sent: String?): String {
    val text = sent?.takeIf { it.isNotBlank() }.orFail { forbidden(ErrorCodes.INVITE_REQUIRED) }
    return Invites.parse(text).orFail { forbidden(ErrorCodes.INVITE_INVALID) }
}

private const val RECOVERY_TOKEN_BYTES = 32

private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

fun Route.loginRoutes(services: Services) {
    post(Api.CHALLENGE) {
        val request = call.receiveJson<ChallengeRequest>()
        ensure(Usernames.isWellFormed(request.username)) { badRequest(ErrorCodes.INVALID_USERNAME) }
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
        ensure(services.lookupBudget.tryAcquire(call.account.id.toString())) { rateLimited(services.lookupBudget) }
        val username = call.parameters["username"].orEmpty()
        ensure(Usernames.isWellFormed(username)) { notFound() }
        val account = services.accounts.find(username).orFail { notFound() }
        // A hidden account answers exactly like a missing one, so a search learns nothing.
        ensure(account.visibleTo(call.account.id, call.request.queryParameters[Api.LINK])) { notFound() }
        call.respond(AccountInfo(account.username, account.identityKey.encodeBase64()))
    }

    delete(Api.ACCOUNT) {
        services.accounts.delete(call.account.id)
        call.respond(HttpStatusCode.NoContent)
    }

    put(Api.RECOVERY) {
        val request = call.receiveJson<RecoveryHash>()
        val hash = request.hash.decodeBase64(ErrorCodes.BAD_REQUEST)
        ensure(hash.size == RECOVERY_TOKEN_BYTES) { badRequest(ErrorCodes.BAD_REQUEST) }
        ensure(services.accounts.setRecovery(call.account.id, hash)) { ApiException(HttpStatusCode.Conflict, ErrorCodes.BAD_REQUEST) }
        call.respond(HttpStatusCode.NoContent)
    }

    put(Api.DISCOVERY) {
        val request = call.receiveJson<Discovery>()
        services.accounts.setDiscoverable(call.account.id, request.discoverable)
        call.respond(request)
    }
}
