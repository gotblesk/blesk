package app.blesk.server.socket

import app.blesk.server.BleskJson
import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.keys.isLow
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ClientFrame
import app.blesk.shared.protocol.ServerFrame
import io.ktor.server.routing.Route
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException

private const val QUEUE_PAGE = 100

fun Route.socketRoutes(services: Services) {
    webSocket(Api.SOCKET) {
        val accountId = call.account.id
        val connection = SocketConnection(accountId)
        // Registered before the queue is read, so a message stored meanwhile is either in the
        // queue snapshot or in the outbox; ids already sent from the queue are skipped later.
        services.hub.register(connection)
        try {
            val delivered = drainQueue(services, accountId)
            sendFrame(ServerFrame.Synced)
            val keys = services.keys.status(accountId)
            if (keys.isLow()) sendFrame(ServerFrame.KeysLow(keys.preKeys, keys.kyberPreKeys))

            val writer = launch {
                for (frame in connection.outbox) {
                    if (frame is ServerFrame.Deliver && frame.envelope.serverId in delivered) continue
                    sendFrame(frame)
                }
                close(CloseReason(CloseReason.Codes.TRY_AGAIN_LATER, "outbox overflow"))
            }
            for (frame in incoming) {
                if (frame is Frame.Text) handleClientFrame(services, accountId, frame.readText())
            }
            writer.cancel()
        } finally {
            services.hub.unregister(connection)
            connection.outbox.close()
        }
    }
}

private suspend fun DefaultWebSocketServerSession.drainQueue(services: Services, accountId: Long): Set<Long> {
    val delivered = HashSet<Long>()
    var after = 0L
    while (true) {
        val page = services.messages.queued(accountId, after, QUEUE_PAGE)
        page.forEach { envelope ->
            sendFrame(ServerFrame.Deliver(envelope))
            delivered += envelope.serverId
        }
        if (page.size < QUEUE_PAGE) return delivered
        after = page.last().serverId
    }
}

private suspend fun handleClientFrame(services: Services, accountId: Long, text: String) {
    // Unknown or malformed frames are ignored so newer clients can talk to an older server.
    val frame = try {
        BleskJson.decodeFromString(ClientFrame.serializer(), text)
    } catch (_: SerializationException) {
        return
    } catch (_: IllegalArgumentException) {
        return
    }
    when (frame) {
        is ClientFrame.Ack -> services.messages.acknowledge(accountId, frame.serverId)
    }
}

private suspend fun DefaultWebSocketServerSession.sendFrame(frame: ServerFrame) {
    send(Frame.Text(BleskJson.encodeToString(ServerFrame.serializer(), frame)))
}
