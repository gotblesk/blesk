package app.blesk.server.bots

import app.blesk.server.Services
import app.blesk.server.auth.Tokens
import app.blesk.server.auth.account
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.conflict
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.rateLimited
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.BotCreated
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CreateBot
import app.blesk.shared.protocol.IncomingCallback
import app.blesk.shared.protocol.IncomingMessage
import app.blesk.shared.protocol.Update
import app.blesk.shared.protocol.UserToBot
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post

private const val MAX_NAME = 64
private const val MAX_ABOUT = 280
private const val MAX_CLIENT_ID = 128

/** Owner and people side of bots; everything here is called by a signed-in person. */
fun Route.botRoutes(services: Services) {
    post(Bots.PATH) {
        val owner = call.account
        ensure(services.botRateLimiter.tryAcquire(owner.id.toString())) { rateLimited(services.botRateLimiter) }
        val request = call.receiveJson<CreateBot>()
        ensure(Bots.isValidUsername(request.username)) { badRequest(ErrorCodes.INVALID_USERNAME) }
        val name = request.name.trim()
        ensure(name.length in 1..MAX_NAME) { badRequest(ErrorCodes.INVALID_NAME) }
        val about = request.about.trim()
        ensure(about.length <= MAX_ABOUT) { badRequest(ErrorCodes.INVALID_NAME) }
        val token = newBotToken(request.username)
        when (services.bots.create(request.username, owner.id, name, about, Tokens.hash(token), services.now())) {
            BotCreation.Created -> {
                val bot = checkNotNull(services.bots.find(request.username))
                call.respond(HttpStatusCode.Created, BotCreated(bot.info(), token))
            }
            BotCreation.Taken -> throw conflict(ErrorCodes.USERNAME_TAKEN)
            BotCreation.TooMany -> throw conflict(ErrorCodes.TOO_MANY_BOTS)
        }
    }

    get("${Bots.PATH}/mine") {
        call.respond(services.bots.owned(call.account.id).map(Bot::info))
    }

    get("${Bots.PATH}/{username}") {
        call.respond(call.pathBot(services).info())
    }

    post("${Bots.PATH}/{username}/token") {
        val bot = call.ownedBot(services)
        val token = newBotToken(bot.username)
        services.bots.replaceToken(bot.id, Tokens.hash(token))
        call.respond(BotCreated(bot.info(), token))
    }

    delete("${Bots.PATH}/{username}") {
        val bot = call.ownedBot(services)
        services.bots.delete(bot.id)
        services.botSignals.forget(bot.id)
        call.respond(HttpStatusCode.NoContent)
    }

    post("${Bots.PATH}/{username}/updates") {
        val person = call.account
        ensure(services.botRateLimiter.tryAcquire(person.id.toString())) { rateLimited(services.botRateLimiter) }
        val bot = call.pathBot(services)
        val request = call.receiveJson<UserToBot>()
        ensure(request.id.length in 1..MAX_CLIENT_ID) { badRequest() }
        ensure((request.text == null) != (request.callback == null)) { badRequest() }
        val now = services.now()
        val text = request.text
        var callbackId: String? = null
        val update = if (text != null) {
            ensure(isValidText(text)) { badRequest(ErrorCodes.INVALID_TEXT) }
            Update(0, message = IncomingMessage(request.id, person.username, text, now.toEpochMilli()))
        } else {
            val press = checkNotNull(request.callback)
            ensure(press.data.length in 1..Bots.MAX_CALLBACK_DATA) { badRequest() }
            // Only buttons under this bot's messages to this very person can be pressed.
            ensure(services.bots.messageChat(bot.id, press.messageId) == person.id) { notFound() }
            callbackId = Tokens.newId()
            Update(0, callback = IncomingCallback(callbackId, person.username, press.messageId, press.data))
        }
        val enqueued = services.bots.enqueue(bot.id, person.id, request.id, callbackId, update, now)
        if (enqueued.isNew) dispatchUpdate(call.application, services, bot, enqueued.update)
        call.respond(HttpStatusCode.NoContent)
    }
}

/** `<username>:<random>`: the name tells the owner which bot a token belongs to; only the random part is secret. */
private fun newBotToken(username: String) = "$username:${Tokens.newToken()}"

private suspend fun ApplicationCall.pathBot(services: Services): Bot {
    val username = parameters["username"].orEmpty()
    ensure(Bots.isValidUsername(username)) { notFound() }
    return services.bots.find(username).orFail { notFound() }
}

private suspend fun ApplicationCall.ownedBot(services: Services): Bot {
    val bot = pathBot(services)
    ensure(bot.ownerId == account.id) { forbidden() }
    return bot
}

internal fun isValidText(text: String) = text.isNotBlank() && text.length <= Bots.MAX_TEXT
