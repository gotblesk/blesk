package app.blesk.core.crypto.signal

import app.blesk.core.data.db.SignalStateDao
import app.blesk.core.data.db.SignalStateEntity
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.util.UUID

/**
 * libsignal's protocol store, persisted in the encrypted database. Remote identities are
 * trusted on first use; a changed identity is saved but reported so the UI can warn.
 */
class SignalStore(private val dao: SignalStateDao) : SignalProtocolStore {

    /**
     * Held for every libsignal operation that reads and writes session or sender-key state.
     * libsignal loads a whole record, advances it and stores it back without a lock of its own, so
     * a message being encrypted while another from the same person is decrypted would lose one of
     * the two ratchet steps, and later messages would no longer decrypt.
     */
    val lock = Any()

    val stateDao: SignalStateDao get() = dao

    /** Called when someone's identity key changes, so the chat can show a warning. */
    var onIdentityChanged: (String) -> Unit = {}

    fun hasLocalIdentity(): Boolean = dao.get(LOCAL, IDENTITY) != null

    fun saveLocalIdentity(pair: IdentityKeyPair, registrationId: Int) {
        dao.put(SignalStateEntity(LOCAL, IDENTITY, pair.serialize(), registrationId.toLong()))
    }

    override fun getIdentityKeyPair(): IdentityKeyPair =
        IdentityKeyPair(requireNotNull(dao.get(LOCAL, IDENTITY)) { "No local identity" }.record)

    override fun getLocalRegistrationId(): Int = requireNotNull(dao.get(LOCAL, IDENTITY)).extra.toInt()

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        val existing = getIdentity(address)
        dao.put(SignalStateEntity(IDENTITIES, address.name, identityKey.serialize()))
        return if (existing != null && existing != identityKey) {
            onIdentityChanged(address.name)
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        } else {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean = true

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        dao.get(IDENTITIES, address.name)?.let { IdentityKey(it.record) }

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        dao.get(PREKEYS, preKeyId.toString())?.let { PreKeyRecord(it.record) } ?: throw InvalidKeyIdException("No prekey $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) =
        dao.put(SignalStateEntity(PREKEYS, preKeyId.toString(), record.serialize()))

    override fun containsPreKey(preKeyId: Int): Boolean = dao.get(PREKEYS, preKeyId.toString()) != null

    override fun removePreKey(preKeyId: Int) = dao.delete(PREKEYS, preKeyId.toString())

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        dao.get(SIGNED, signedPreKeyId.toString())?.let { SignedPreKeyRecord(it.record) }
            ?: throw InvalidKeyIdException("No signed prekey $signedPreKeyId")

    override fun loadSignedPreKeys(): List<SignedPreKeyRecord> = dao.all(SIGNED).map { SignedPreKeyRecord(it.record) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) =
        dao.put(SignalStateEntity(SIGNED, signedPreKeyId.toString(), record.serialize()))

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = dao.get(SIGNED, signedPreKeyId.toString()) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) = dao.delete(SIGNED, signedPreKeyId.toString())

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        dao.get(KYBER, kyberPreKeyId.toString())?.let { KyberPreKeyRecord(it.record) }
            ?: throw InvalidKeyIdException("No kyber prekey $kyberPreKeyId")

    override fun loadKyberPreKeys(): List<KyberPreKeyRecord> = dao.all(KYBER).map { KyberPreKeyRecord(it.record) }

    /** [lastResort] keys are kept after use; one-time ones are deleted. */
    fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord, lastResort: Boolean) =
        dao.put(SignalStateEntity(KYBER, kyberPreKeyId.toString(), record.serialize(), if (lastResort) 1 else 0))

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) =
        storeKyberPreKey(kyberPreKeyId, record, lastResort = false)

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = dao.get(KYBER, kyberPreKeyId.toString()) != null

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        val entity = dao.get(KYBER, kyberPreKeyId.toString()) ?: return
        if (entity.extra == 0L) dao.delete(KYBER, kyberPreKeyId.toString())
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        dao.get(SESSIONS, address.toString())?.let { SessionRecord(it.record) }

    override fun loadExistingSessions(addresses: List<SignalProtocolAddress>): List<SessionRecord> =
        addresses.map { loadSession(it) ?: throw NoSessionException("No session for $it") }

    override fun getSubDeviceSessions(name: String): List<Int> = emptyList()

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) =
        dao.put(SignalStateEntity(SESSIONS, address.toString(), record.serialize()))

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        loadSession(address)?.hasSenderChain() == true

    override fun deleteSession(address: SignalProtocolAddress) = dao.delete(SESSIONS, address.toString())

    override fun deleteAllSessions(name: String) = dao.deleteByPrefix(SESSIONS, "$name.")

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) =
        dao.put(SignalStateEntity(SENDER_KEYS, "$sender::$distributionId", record.serialize()))

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? =
        dao.get(SENDER_KEYS, "$sender::$distributionId")?.let { SenderKeyRecord(it.record) }

    fun count(kind: String) = dao.count(kind)

    companion object {
        const val LOCAL = "local"
        const val IDENTITY = "identity"
        const val IDENTITIES = "identity"
        const val PREKEYS = "prekey"
        const val SIGNED = "signed"
        const val KYBER = "kyber"
        const val SESSIONS = "session"
        const val SENDER_KEYS = "senderkey"
    }
}
