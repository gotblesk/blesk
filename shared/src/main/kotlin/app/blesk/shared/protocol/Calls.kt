package app.blesk.shared.protocol

import kotlinx.serialization.Serializable

/**
 * Calls run through a LiveKit server next to blesk. The blesk server only hands out short-lived
 * join tokens; media is end-to-end encrypted with a key that travels inside Signal messages
 * (one-to-one calls) or inside the group state (voice channels), so LiveKit relays frames it
 * cannot read.
 */
object Calls {
    const val TOKEN = "/v1/calls/token"

    /** Room names: `call-<uuid>` for one-to-one calls, `voice-<group>-<channel>` for voice channels. */
    private val roomPattern = Regex("^[A-Za-z0-9_-]{1,128}$")

    fun isValidRoom(room: String) = roomPattern.matches(room)

    fun directRoom(callId: String) = "call-$callId"

    fun voiceRoom(groupId: String, channelId: String) = "voice-$groupId-$channelId"

    /** Size of the media key, before Base64. */
    const val KEY_BYTES = 32
}

@Serializable
data class CallTokenRequest(val room: String)

/** [url] is the LiveKit WebSocket address as the phone should reach it; [token] lives a few hours. */
@Serializable
data class CallToken(val url: String, val token: String)
