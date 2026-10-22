package app.blesk.core.crypto.signal

import app.blesk.core.crypto.signal.KeyManager.Companion.b64
import app.blesk.core.crypto.signal.KeyManager.Companion.unb64
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Content
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.groups.GroupCipher
import org.signal.libsignal.protocol.groups.GroupSessionBuilder
import org.signal.libsignal.protocol.message.SenderKeyDistributionMessage
import java.util.UUID

/**
 * Group encryption with libsignal SenderKeys: each member encrypts once with a sender key of their own, which they
 * hand over the one-to-one Signal sessions to those who may read what it encrypts. Which key a message goes under,
 * and when a key is replaced by one with a new [UUID], is the caller's choice; a key is never derived from anything.
 */
class GroupCrypto(private val store: SignalStore, private val localUsername: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Our sender key [distributionId], packaged for a reader; made on first use. Asked again, it hands out the chain
     * as it stands, so a reader who joins later reads from then on, not what came before.
     */
    fun distribution(distributionId: UUID): String =
        b64(synchronized(store.lock) { GroupSessionBuilder(store).create(local(), distributionId) }.serialize())

    fun accept(sender: String, distribution: String) {
        val message = SenderKeyDistributionMessage(unb64(distribution))
        synchronized(store.lock) { GroupSessionBuilder(store).process(address(sender), message) }
    }

    /** Throws if [distribution] was never asked for [distributionId]: no reader could open what it encrypts. */
    fun encrypt(distributionId: UUID, content: Content): String {
        val plain = Padding.pad(json.encodeToString(Content.serializer(), content).encodeToByteArray())
        return b64(synchronized(store.lock) { GroupCipher(store, local()).encrypt(distributionId, plain) }.serialize())
    }

    /** Throws if the sender's key for this group has not arrived or the message was tampered with. */
    fun decrypt(sender: String, body: String): Content {
        val bytes = unb64(body)
        val plain = synchronized(store.lock) { GroupCipher(store, address(sender)).decrypt(bytes) }
        return json.decodeFromString(Content.serializer(), Padding.unpad(plain).decodeToString())
    }

    private fun local() = address(localUsername())

    private fun address(username: String) = SignalProtocolAddress(username, Api.DEVICE_ID)
}
