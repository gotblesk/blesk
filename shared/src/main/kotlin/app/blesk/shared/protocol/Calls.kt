package app.blesk.shared.protocol

import kotlinx.serialization.Serializable

/**
 * Calls run through a LiveKit server next to blesk. The blesk server only hands out short-lived
 * join tokens; media is end-to-end encrypted with a key that travels inside Signal messages
 * (one-to-one calls, closed voice channels) or inside the group state (voice channels open to the
 * whole room), so LiveKit relays frames it cannot read.
 */
object Calls {
    const val TOKEN = "/v1/calls/token"

    /**
     * Room names: `call-<uuid>` for one-to-one calls, `voice-<group>-<channel>` for voice channels open to the whole
     * room, and that followed by `-<secret>` for closed ones.
     */
    private val roomPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun isValidRoom(room: String) = roomPattern.matches(room)

    fun directRoom(callId: String) = "call-$callId"

    fun voiceRoom(groupId: String, channelId: String) = "voice-$groupId-$channelId"

    /**
     * A closed voice channel's room. The [secret] is new with every key of the channel, so someone who no longer sees
     * the channel cannot find its calls any more, nor see who is in them.
     */
    fun closedVoiceRoom(groupId: String, channelId: String, secret: String) = "${voiceRoom(groupId, channelId)}-$secret"

    /** Whether [room] can be one of [closedVoiceRoom] for this channel. */
    fun isClosedVoiceRoom(room: String, groupId: String, channelId: String): Boolean {
        val prefix = "${voiceRoom(groupId, channelId)}-"
        return isValidRoom(room) && room.startsWith(prefix) && room.length > prefix.length
    }

    /** Size of the random part of a closed voice channel's room name, before Base64. */
    const val ROOM_SECRET_BYTES = 16

    /** Size of the media key, before Base64. */
    const val KEY_BYTES = 32
}

@Serializable
data class CallTokenRequest(val room: String)

/** [url] is the LiveKit WebSocket address as the phone should reach it; [token] lives a few hours. */
@Serializable
data class CallToken(val url: String, val token: String)
