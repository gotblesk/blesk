package app.blesk.server

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ClientFrame
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.OutgoingMessage
import app.blesk.shared.protocol.SendResult
import app.blesk.shared.protocol.ServerFrame
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import kotlin.random.Random

class MessagesTest {
    @Test
    fun `a live recipient gets the message and its ack removes it from the queue`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            assertEquals(ServerFrame.Synced, nextFrame())
            assertTrue(nextFrame() is ServerFrame.KeysLow)

            val body = Random.nextBytes(64).b64()
            val sent = harness.client.send(alice, bob.username, message(body = body)).body<SendResult>()

            val delivered = nextFrame() as ServerFrame.Deliver
            assertEquals(sent.serverId, delivered.envelope.serverId)
            assertEquals(alice.username, delivered.envelope.sender)
            assertEquals(body, delivered.envelope.body)
            assertEquals(EnvelopeType.WHISPER, delivered.envelope.type)
            assertEquals(sent.timestampMillis, delivered.envelope.timestampMillis)

            ack(delivered.envelope.serverId)
            val bobId = harness.accountId(bob)
            eventually { assertEquals(emptyList<Any>(), harness.services.messages.queued(bobId, 0, 10)) }
        }
    }

    @Test
    fun `queued messages arrive in order on connect, followed by synced`() = serverTest { harness ->
        val alice = harness.register()
        val carol = harness.register()
        val bob = harness.register()
        harness.uploadKeys(bob, harness.keys(bob, preKeys = 25, kyberPreKeys = 25))
        val ids = listOf(alice, carol, alice).map { sender ->
            harness.client.send(sender, bob.username, message()).body<SendResult>().serverId
        }

        harness.client.webSocket("${Api.SOCKET}?token=${bob.token}") {
            val frames = List(3) { nextFrame() as ServerFrame.Deliver }
            assertEquals(ids, frames.map { it.envelope.serverId })
            assertEquals(listOf(alice.username, carol.username, alice.username), frames.map { it.envelope.sender })
            assertEquals(ServerFrame.Synced, nextFrame())
        }
        // Not acknowledged, so a second connection receives the same messages again.
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            assertEquals(ids, List(3) { (nextFrame() as ServerFrame.Deliver).envelope.serverId })
        }
    }

    @Test
    fun `every open socket of the recipient receives the message`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        harness.uploadKeys(bob, harness.keys(bob, preKeys = 25, kyberPreKeys = 25))
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            val first = this
            assertEquals(ServerFrame.Synced, first.nextFrame())
            harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
                assertEquals(ServerFrame.Synced, nextFrame())
                val serverId = harness.client.send(alice, bob.username, message()).body<SendResult>().serverId
                assertEquals(serverId, (nextFrame() as ServerFrame.Deliver).envelope.serverId)
                assertEquals(serverId, (first.nextFrame() as ServerFrame.Deliver).envelope.serverId)
            }
        }
    }

    @Test
    fun `resending the same client id stores the message once`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val message = message()
        val first = harness.client.send(alice, bob.username, message).body<SendResult>()
        val retry = harness.client.send(alice, bob.username, message).body<SendResult>()
        assertEquals(first, retry)
        assertEquals(1, harness.services.messages.queued(harness.accountId(bob), 0, 10).size)

        val other = harness.client.send(alice, bob.username, message(clientId = UUID.randomUUID().toString())).body<SendResult>()
        assertNotEquals(first.serverId, other.serverId)
    }

    @Test
    fun `ephemeral messages reach live sockets and are never stored`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        harness.uploadKeys(bob, harness.keys(bob, preKeys = 25, kyberPreKeys = 25))
        val bobId = harness.accountId(bob)

        assertEquals(HttpStatusCode.OK, harness.client.send(alice, bob.username, message(ephemeral = true)).status)
        assertEquals(0, harness.services.messages.queued(bobId, 0, 10).size)

        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            assertEquals(ServerFrame.Synced, nextFrame())
            val sent = harness.client.send(alice, bob.username, message(ephemeral = true)).body<SendResult>()
            assertEquals(sent.serverId, (nextFrame() as ServerFrame.Deliver).envelope.serverId)
            assertEquals(0, harness.services.messages.queued(bobId, 0, 10).size)
        }
    }

    @Test
    fun `sender key messages pass through like any other type`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val message = message().copy(type = EnvelopeType.SENDERKEY)
        assertEquals(HttpStatusCode.OK, harness.client.send(alice, bob.username, message).status)
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            val delivered = nextFrame() as ServerFrame.Deliver
            assertEquals(EnvelopeType.SENDERKEY, delivered.envelope.type)
            assertEquals(message.body, delivered.envelope.body)
        }
    }

    @Test
    fun `sending to an unknown user or an oversized body fails`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        assertEquals(HttpStatusCode.NotFound, harness.client.send(alice, randomUsername(), message()).status)

        val huge = harness.client.send(alice, bob.username, message(body = ByteArray(Api.MAX_MESSAGE_BYTES + 1).b64()))
        assertEquals(HttpStatusCode.PayloadTooLarge, huge.status)
        assertEquals("too_large", huge.body<ErrorBody>().code)
    }

    @Test
    fun `the socket asks for more keys when one-time keys run low`() = serverTest { harness ->
        val bob = harness.register()
        harness.uploadKeys(bob, harness.keys(bob, preKeys = 25, kyberPreKeys = 3))
        harness.client.webSocket(Api.SOCKET, request = { bearerAuth(bob.token) }) {
            assertEquals(ServerFrame.Synced, nextFrame())
            assertEquals(ServerFrame.KeysLow(25, 3), nextFrame())
        }
    }

    @Test
    fun `the socket refuses connections without a valid token`() = serverTest { harness ->
        val failure = runCatching { harness.client.webSocket("${Api.SOCKET}?token=forged") { nextFrame() } }
        assertTrue(failure.isFailure)
    }

    private fun message(
        body: String = Random.nextBytes(32).b64(),
        clientId: String = UUID.randomUUID().toString(),
        ephemeral: Boolean = false,
    ) =
        OutgoingMessage(clientId = clientId, type = EnvelopeType.WHISPER, body = body, ephemeral = ephemeral)

    private suspend fun HttpClient.send(sender: TestUser, recipient: String, message: OutgoingMessage): HttpResponse =
        post("${Api.MESSAGES}/$recipient") {
            bearerAuth(sender.token)
            contentType(ContentType.Application.Json)
            setBody(message)
        }

    private suspend fun WebSocketSession.ack(serverId: Long) {
        send(Frame.Text(BleskJson.encodeToString(ClientFrame.serializer(), ClientFrame.Ack(serverId))))
    }
}
