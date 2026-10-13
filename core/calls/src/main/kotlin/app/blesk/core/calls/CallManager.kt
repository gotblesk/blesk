package app.blesk.core.calls

import app.blesk.core.data.calls.CallOutcome
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.calls.CallSession
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.calls.CallSignaling
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.CallEndReason
import app.blesk.shared.protocol.Calls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * One call at a time. Ringing, answering and hanging up travel as end-to-end encrypted Signal
 * messages; the call itself goes through LiveKit with a media key from the offer. In the demo
 * there is no [signaling]: a call connects to nobody and shows your own camera.
 */
class CallManager(
    private val scope: CoroutineScope,
    val media: MediaSession,
    private val signaling: CallSignaling?,
    private val http: () -> OkHttpClient?,
    private val nameOf: (String) -> String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutable = MutableStateFlow<CallSession?>(null)
    val session: StateFlow<CallSession?> = mutable.asStateFlow()

    /** The other person of a one-to-one call. */
    private var peer: String? = null
    private var key: String? = null
    private var timer: Job? = null
    private val random = SecureRandom()

    val demo: Boolean get() = signaling == null

    init {
        signaling?.let { source -> scope.launch { source.callSignals.collect(::onSignal) } }
        media.onDisconnected = { if (mutable.value?.phase == CallPhase.Active) finish(CallOutcome.Failed) }
        scope.launch {
            media.state.distinctUntilChangedBy { it.othersJoined }.collect { state -> onPresence(state.othersJoined) }
        }
    }

    /** Rings [username]; returns false if a call is already going on. */
    fun call(username: String, video: Boolean): Boolean {
        if (busy()) return false
        val id = UUID.randomUUID().toString()
        val newKey = newKey()
        peer = username
        key = newKey
        mutable.value = CallSession(id, username, nameOf(username), incoming = false, video = video, phase = CallPhase.Calling)
        val signals = signaling
        if (signals == null) {
            // Nobody on the other end: the demo walks through the same steps a real call takes.
            scope.launch {
                delay(DEMO_RING_MILLIS)
                mutable.value = mutable.value?.takeIf { it.id == id && it.phase == CallPhase.Calling }?.copy(phase = CallPhase.Connecting)
                    ?: return@launch
                delay(DEMO_CONNECT_MILLIS)
                activate(id)
            }
            return true
        }
        scope.launch {
            signals.sendCallSignal(username, Body.CallOffer(id, video, newKey))
            // Joining the room while it rings makes the answer feel instant.
            if (!join(Calls.directRoom(id), newKey, video)) finish(CallOutcome.Unavailable)
        }
        startTimer(id, RING_MILLIS) {
            scope.launch { signals.sendCallSignal(username, Body.CallEnd(id, CallEndReason.Missed)) }
            finish(CallOutcome.NoAnswer)
        }
        return true
    }

    fun accept() {
        val current = mutable.value ?: return
        if (!current.incoming || current.phase != CallPhase.Ringing) return
        val to = peer ?: return
        val callKey = key ?: return
        timer?.cancel()
        mutable.value = current.copy(phase = CallPhase.Connecting)
        scope.launch {
            signaling?.sendCallSignal(to, Body.CallAnswer(current.id))
            if (join(Calls.directRoom(current.id), callKey, current.video)) activate(current.id) else finish(CallOutcome.Unavailable)
        }
    }

    fun decline() {
        val current = mutable.value ?: return
        peer?.let { to -> scope.launch { signaling?.sendCallSignal(to, Body.CallEnd(current.id, CallEndReason.Declined)) } }
        finish(CallOutcome.Declined)
    }

    fun hangUp() {
        val current = mutable.value ?: return
        if (current.phase == CallPhase.Ended) return
        if (!current.voiceChannel) {
            peer?.let { to -> scope.launch { signaling?.sendCallSignal(to, Body.CallEnd(current.id, CallEndReason.HangUp)) } }
        }
        finish(CallOutcome.HangUp)
    }

    /** Joins a room's voice channel; nobody is rung. */
    fun joinVoice(groupId: String, channelId: String, conversationId: String, title: String): Boolean {
        val signals = signaling ?: return false
        if (busy()) return mutable.value?.conversationId == conversationId
        val id = Calls.voiceRoom(groupId, channelId)
        peer = null
        mutable.value = CallSession(
            id = id,
            conversationId = conversationId,
            title = title,
            incoming = false,
            video = false,
            phase = CallPhase.Connecting,
            voiceChannel = true,
        )
        scope.launch {
            val voiceKey = signals.voiceKey(groupId)
            key = voiceKey
            if (voiceKey != null && join(id, voiceKey, video = false)) activate(id) else finish(CallOutcome.Unavailable)
        }
        return true
    }

    private fun busy() = mutable.value?.let { it.phase != CallPhase.Ended } == true

    private suspend fun join(room: String, mediaKey: String, video: Boolean): Boolean {
        val signals = signaling ?: return false
        val token = signals.callToken(room) ?: return false
        return media.connect(token.url, token.token, mediaKey, video, http())
    }

    private fun onSignal(signal: CallSignal) {
        val current = mutable.value
        when (val body = signal.body) {
            is Body.CallOffer -> onOffer(signal, body, current)
            is Body.CallAnswer -> if (current?.id == body.callId && signal.from == peer && !current.incoming) {
                timer?.cancel()
                if (media.state.value.connected) activate(body.callId) else mutable.value = current.copy(phase = CallPhase.Connecting)
            }
            is Body.CallEnd -> if (current?.id == body.callId && signal.from == peer) {
                finish(
                    when (body.reason) {
                        CallEndReason.Declined -> CallOutcome.Declined
                        CallEndReason.Busy -> CallOutcome.Busy
                        CallEndReason.Missed -> CallOutcome.Missed
                        CallEndReason.HangUp -> CallOutcome.HangUp
                    },
                )
            }
            else -> Unit
        }
    }

    private fun onOffer(signal: CallSignal, offer: Body.CallOffer, current: CallSession?) {
        // An offer that waited on the server while the phone was offline rings nobody.
        if (clock() - signal.sentAtMillis > STALE_OFFER_MILLIS) return
        if (current != null && current.phase != CallPhase.Ended) {
            if (current.id != offer.callId) {
                scope.launch { signaling?.sendCallSignal(signal.from, Body.CallEnd(offer.callId, CallEndReason.Busy)) }
            }
            return
        }
        peer = signal.from
        key = offer.key
        mutable.value = CallSession(
            id = offer.callId,
            conversationId = signal.from,
            title = nameOf(signal.from),
            incoming = true,
            video = offer.video,
            phase = CallPhase.Ringing,
        )
        startTimer(offer.callId, RING_MILLIS) { finish(CallOutcome.Missed) }
    }

    /** One-to-one calls end when the other side is gone for a while without saying so. */
    private fun onPresence(othersJoined: Boolean) {
        val current = mutable.value ?: return
        if (current.voiceChannel || current.phase != CallPhase.Active) return
        if (othersJoined) timer?.cancel() else startTimer(current.id, ALONE_MILLIS) { finish(CallOutcome.HangUp) }
    }

    private fun activate(id: String) {
        val current = mutable.value
        if (current?.id != id || current.phase == CallPhase.Ended) return
        mutable.value = current.copy(phase = CallPhase.Active, startedAtMillis = clock())
        // The other side said yes but may never make it into the room.
        if (!demo && !current.voiceChannel && !media.state.value.othersJoined) {
            startTimer(id, ALONE_MILLIS) { finish(CallOutcome.Failed) }
        }
    }

    private fun startTimer(id: String, millis: Long, onTimeout: () -> Unit) {
        timer?.cancel()
        timer = scope.launch {
            delay(millis)
            if (mutable.value?.id == id) onTimeout()
        }
    }

    private fun finish(outcome: CallOutcome) {
        val current = mutable.value ?: return
        timer?.cancel()
        media.disconnect()
        peer = null
        key = null
        mutable.value = current.copy(phase = CallPhase.Ended, outcome = outcome)
        scope.launch {
            delay(ENDED_SHOWN_MILLIS)
            mutable.update { if (it?.id == current.id && it.phase == CallPhase.Ended) null else it }
        }
    }

    private fun newKey(): String = Base64.getEncoder().encodeToString(ByteArray(Calls.KEY_BYTES).also(random::nextBytes))

    private companion object {
        const val RING_MILLIS = 45_000L
        const val STALE_OFFER_MILLIS = 60_000L
        const val ALONE_MILLIS = 30_000L
        const val ENDED_SHOWN_MILLIS = 1_800L
        const val DEMO_RING_MILLIS = 1_800L
        const val DEMO_CONNECT_MILLIS = 900L
    }
}
