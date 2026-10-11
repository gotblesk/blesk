package app.blesk.server.socket

import app.blesk.shared.protocol.ServerFrame
import kotlinx.coroutines.channels.Channel
import java.util.concurrent.ConcurrentHashMap

/** One open socket. Frames pushed from other requests wait in [outbox] until the socket's writer sends them. */
class SocketConnection(val accountId: Long) {
    val outbox = Channel<ServerFrame>(OUTBOX_CAPACITY)

    /**
     * A reader that cannot keep up loses its outbox and gets disconnected; everything durable is still
     * in the queue and arrives again on reconnect.
     */
    fun offer(frame: ServerFrame) {
        if (outbox.trySend(frame).isFailure) outbox.close()
    }

    private companion object {
        const val OUTBOX_CAPACITY = 1024
    }
}

/** Live sockets per account. An account may have several at once, and every one of them gets every frame. */
class SocketHub {
    private val connections = ConcurrentHashMap<Long, MutableSet<SocketConnection>>()

    fun register(connection: SocketConnection) {
        connections.compute(connection.accountId) { _, existing ->
            (existing ?: ConcurrentHashMap.newKeySet()).apply { add(connection) }
        }
    }

    fun unregister(connection: SocketConnection) {
        connections.computeIfPresent(connection.accountId) { _, existing ->
            existing.remove(connection)
            existing.ifEmpty { null }
        }
    }

    fun isOnline(accountId: Long): Boolean = !connections[accountId].isNullOrEmpty()

    fun push(accountId: Long, frame: ServerFrame) {
        connections[accountId]?.forEach { it.offer(frame) }
    }
}
