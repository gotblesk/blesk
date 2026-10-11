package app.blesk.core.network

import app.blesk.shared.protocol.ClientFrame
import app.blesk.shared.protocol.ServerFrame
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.bearerAuth
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import java.io.IOException

/**
 * The live delivery channel. Reconnects on its own with exponential backoff; envelopes that
 * arrive while offline wait on the server and come in on the next connection.
 */
/** Live delivery of envelopes; acknowledged ones are removed from the server. */
interface Transport {
    val frames: SharedFlow<ServerFrame>
    val connected: StateFlow<Boolean>

    fun start()

    fun ack(serverId: Long)

    fun stop()
}

class Socket(private val api: BleskApi, private val scope: CoroutineScope) : Transport {
    private val frameFlow = MutableSharedFlow<ServerFrame>(extraBufferCapacity = BUFFER)
    private val online = MutableStateFlow(false)
    private val pending = Channel<ClientFrame>(Channel.UNLIMITED)
    private var job: Job? = null

    override val frames: SharedFlow<ServerFrame> = frameFlow.asSharedFlow()
    override val connected: StateFlow<Boolean> = online.asStateFlow()

    override fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            var backoff = MIN_BACKOFF_MILLIS
            while (isActive) {
                try {
                    api.client.webSocket(urlString = api.socketUrl(), request = { api.token?.let { bearerAuth(it) } }) {
                        online.value = true
                        backoff = MIN_BACKOFF_MILLIS
                        val writer = launch {
                            for (frame in pending) {
                                send(Frame.Text(api.json.encodeToString(ClientFrame.serializer(), frame)))
                            }
                        }
                        try {
                            for (frame in incoming) {
                                if (frame !is Frame.Text) continue
                                val parsed = try {
                                    api.json.decodeFromString(ServerFrame.serializer(), frame.readText())
                                } catch (_: SerializationException) {
                                    null
                                }
                                parsed?.let { frameFlow.emit(it) }
                            }
                        } finally {
                            writer.cancel()
                        }
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: IOException) {
                    // Network trouble: fall through to the backoff below.
                } catch (_: IllegalStateException) {
                    // Handshake refused (for example an expired token): retry later.
                } catch (_: Exception) {
                    // Any other transport failure is treated the same way.
                }
                online.value = false
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MILLIS)
            }
        }
    }

    override fun ack(serverId: Long) {
        pending.trySend(ClientFrame.Ack(serverId))
    }

    override fun stop() {
        job?.cancel()
        job = null
        online.value = false
    }

    private companion object {
        const val BUFFER = 64
        const val MIN_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
    }
}
