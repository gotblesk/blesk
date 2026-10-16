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
 * Group encryption with libsignal SenderKeys: each member encrypts once with their own sender
 * key, which they hand to every other member over the one-to-one Signal sessions. A new epoch
 * (someone left or was removed) means new sender keys that the departed never receive.
 */
class GroupCrypto(private val store: SignalStore, private val localUsername: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The sender key for this group and epoch, packaged for distribution to members. */
    fun distribution(groupId: String, epoch: Int): String =
        b64(synchronized(store.lock) { GroupSessionBuilder(store).create(local(), distributionId(groupId, epoch)) }.serialize())

    fun accept(sender: String, distribution: String) {
        val message = SenderKeyDistributionMessage(unb64(distribution))
        synchronized(store.lock) { GroupSessionBuilder(store).process(address(sender), message) }
    }

    fun encrypt(groupId: String, epoch: Int, content: Content): String {
        val plain = Padding.pad(json.encodeToString(Content.serializer(), content).encodeToByteArray())
        return b64(synchronized(store.lock) { GroupCipher(store, local()).encrypt(distributionId(groupId, epoch), plain) }.serialize())
    }

    /** Throws if the sender's key for this group has not arrived or the message was tampered with. */
    fun decrypt(sender: String, body: String): Content {
        val bytes = unb64(body)
        val plain = synchronized(store.lock) { GroupCipher(store, address(sender)).decrypt(bytes) }
        return json.decodeFromString(Content.serializer(), Padding.unpad(plain).decodeToString())
    }

    private fun local() = address(localUsername())

    private fun address(username: String) = SignalProtocolAddress(username, Api.DEVICE_ID)

    companion object {
        fun distributionId(groupId: String, epoch: Int): UUID = UUID.nameUUIDFromBytes("blesk-group:$groupId:$epoch".encodeToByteArray())
    }
}
