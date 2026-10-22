package app.blesk.core.data.calls

import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.CallToken
import kotlinx.coroutines.flow.SharedFlow

enum class CallPhase {
    /** Someone is calling you. */
    Ringing,

    /** You are calling and waiting for an answer. */
    Calling,

    /** Picked up; media is connecting or reconnecting. */
    Connecting,
    Active,
    Ended,
}

enum class CallOutcome { HangUp, Declined, Busy, Missed, NoAnswer, Failed, Unavailable }

/** A call as the screens see it; media details live in the calls module. */
data class CallSession(
    val id: String,
    /** The chat the call belongs to: the person, or the room channel for voice channels. */
    val conversationId: String,
    val title: String,
    val incoming: Boolean,
    val video: Boolean,
    val phase: CallPhase,
    /** A voice channel of a room: no ringing, people come and go. */
    val voiceChannel: Boolean = false,
    /** When the call became active, for the timer. */
    val startedAtMillis: Long? = null,
    val outcome: CallOutcome? = null,
)

/** A call message that came in through an end-to-end encrypted session. */
data class CallSignal(val from: String, val sentAtMillis: Long, val body: Body)

/** What calls need from the server side of the app; implemented where there is a real server. */
interface CallSignaling {
    val me: String

    val callSignals: SharedFlow<CallSignal>

    /** Sends a call message end-to-end encrypted to [to]. */
    suspend fun sendCallSignal(to: String, body: Body)

    /** A join token for a LiveKit room; null when the server has no calls or is unreachable. */
    suspend fun callToken(room: String): CallToken?

    /** Where to talk in a room's voice channel, and with which key; null for a channel we may not join. */
    suspend fun voiceAccess(groupId: String, channelId: String): VoiceAccess?
}

/** A voice channel's LiveKit [room] and the Base64 media [key] its calls are encrypted with. */
data class VoiceAccess(val room: String, val key: String)
