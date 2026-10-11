package app.blesk.server.messages

import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.http.badRequest
import app.blesk.server.http.decodeBase64
import app.blesk.server.http.ensure
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.server.http.tooLarge
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.SendResult
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.Usernames
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post

/** Base64 grows the body by a third; the rest is room for the other JSON fields. */
private const val MESSAGE_REQUEST_LIMIT = Api.MAX_MESSAGE_BYTES * 4L / 3 + 4096

private const val MAX_CLIENT_ID_LENGTH = 128

fun Route.messageRoutes(services: Services) {
    post("${Api.MESSAGES}/{username}") {
        val sender = call.account
        val message = call.receiveJson<OutgoingMessage>(MESSAGE_REQUEST_LIMIT)
        ensure(message.clientId.isNotEmpty() && message.clientId.length <= MAX_CLIENT_ID_LENGTH) { badRequest() }
        val body = message.body.decodeBase64()
        ensure(body.size <= Api.MAX_MESSAGE_BYTES) { tooLarge() }
        val username = call.parameters["username"].orEmpty()
        ensure(Usernames.isValid(username)) { notFound() }
        val recipient = services.accounts.find(username).orFail { notFound() }
        val now = services.now()

        if (message.ephemeral) {
            val serverId = services.messages.nextServerId()
            if (services.hub.isOnline(recipient.id)) {
                val envelope = Envelope(serverId, sender.username, message.type, message.body, now.toEpochMilli())
                services.hub.push(recipient.id, ServerFrame.Deliver(envelope))
            }
            call.respond(SendResult(serverId, now.toEpochMilli()))
            return@post
        }

        when (val stored = services.messages.store(sender.id, sender.username, recipient.id, message.clientId, message.type, body, now)) {
            is Stored.Queued -> {
                services.hub.push(recipient.id, ServerFrame.Deliver(stored.envelope))
                call.respond(SendResult(stored.envelope.serverId, stored.envelope.timestampMillis))
            }
            is Stored.Duplicate -> call.respond(stored.result)
        }
    }
}
