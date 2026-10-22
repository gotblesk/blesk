package app.blesk.shared.protocol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.UUID

class CallsTest {
    private val group = UUID.randomUUID().toString()
    private val channel = UUID.randomUUID().toString().take(8)
    private val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(Calls.ROOM_SECRET_BYTES) { 0x7f })

    @Test
    fun `a closed voice channel's room is a name the server takes`() {
        val room = Calls.closedVoiceRoom(group, channel, secret)
        assertTrue(Calls.isValidRoom(room))
        assertTrue(Calls.isClosedVoiceRoom(room, group, channel))
    }

    @Test
    fun `only a room with a secret after its own channel is that channel's closed room`() {
        assertFalse(Calls.isClosedVoiceRoom(Calls.voiceRoom(group, channel), group, channel))
        assertFalse(Calls.isClosedVoiceRoom("${Calls.voiceRoom(group, channel)}-", group, channel))
        assertFalse(Calls.isClosedVoiceRoom(Calls.closedVoiceRoom(group, "other", secret), group, channel))
        assertFalse(Calls.isClosedVoiceRoom(Calls.directRoom(group), group, channel))
        assertFalse(Calls.isClosedVoiceRoom(Calls.closedVoiceRoom(group, channel, "a/b"), group, channel))
    }
}
