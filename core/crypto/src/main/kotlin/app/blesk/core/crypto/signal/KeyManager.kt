package app.blesk.core.crypto.signal

import app.blesk.core.data.db.SignalStateEntity
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.KeyUpload
import app.blesk.shared.protocol.OneTimeKey
import app.blesk.shared.protocol.RegisterRequest
import app.blesk.shared.protocol.SignedKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import java.security.SecureRandom
import java.util.Base64

/**
 * Creates and rotates this device's keys: the identity key, the signed prekey, a Kyber
 * last-resort key and batches of one-time EC and Kyber prekeys. Private halves stay in the
 * encrypted database; only public halves and signatures are uploaded.
 */
class KeyManager(
    private val store: SignalStore,
    private val random: SecureRandom = SecureRandom(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun ensureIdentity() {
        if (store.hasLocalIdentity()) return
        val registrationId = random.nextInt(MAX_REGISTRATION_ID) + 1
        store.saveLocalIdentity(IdentityKeyPair.generate(), registrationId)
    }

    fun identityPublicKey(): String = b64(store.identityKeyPair.publicKey.serialize())

    fun registration(username: String): RegisterRequest {
        val identity = store.identityKeyPair
        return RegisterRequest(
            username = username,
            identityKey = b64(identity.publicKey.serialize()),
            registrationId = store.localRegistrationId,
            signature = b64(identity.privateKey.calculateSignature(Api.registrationProof(username))),
        )
    }

    fun signAuth(nonce: String): String = b64(store.identityKeyPair.privateKey.calculateSignature(Api.authProof(nonce)))

    /** Everything a fresh account uploads: signed key, last-resort Kyber key and full batches. */
    fun initialUpload(): KeyUpload = KeyUpload(
        signedPreKey = newSignedPreKey(),
        kyberLastResort = newKyberKey(lastResort = true),
        preKeys = newPreKeys(BATCH),
        kyberPreKeys = List(BATCH) { newKyberKey(lastResort = false) },
    )

    /** Tops up one-time keys after the server says they run low. */
    fun refill(preKeysLeft: Int, kyberLeft: Int): KeyUpload = KeyUpload(
        preKeys = newPreKeys((BATCH - preKeysLeft).coerceAtLeast(0)),
        kyberPreKeys = List((BATCH - kyberLeft).coerceAtLeast(0)) { newKyberKey(lastResort = false) },
    )

    private fun newSignedPreKey(): SignedKey {
        val id = nextId(SIGNED_COUNTER)
        val pair = ECKeyPair.generate()
        val signature = store.identityKeyPair.privateKey.calculateSignature(pair.publicKey.serialize())
        store.storeSignedPreKey(id, SignedPreKeyRecord(id, clock(), pair, signature))
        return SignedKey(id, b64(pair.publicKey.serialize()), b64(signature))
    }

    private fun newPreKeys(count: Int): List<OneTimeKey> = List(count) {
        val id = nextId(PREKEY_COUNTER)
        val pair = ECKeyPair.generate()
        store.storePreKey(id, PreKeyRecord(id, pair))
        OneTimeKey(id, b64(pair.publicKey.serialize()))
    }

    private fun newKyberKey(lastResort: Boolean): SignedKey {
        val id = nextId(KYBER_COUNTER)
        val pair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signature = store.identityKeyPair.privateKey.calculateSignature(pair.publicKey.serialize())
        store.storeKyberPreKey(id, KyberPreKeyRecord(id, clock(), pair, signature), lastResort)
        return SignedKey(id, b64(pair.publicKey.serialize()), b64(signature))
    }

    /** Key ids start at a random point and wrap within the 24-bit range libsignal expects. */
    private fun nextId(counter: String): Int {
        val current = store.counter(counter) ?: (random.nextInt(MAX_KEY_ID - 1) + 1)
        val next = current % MAX_KEY_ID + 1
        store.setCounter(counter, next)
        return current
    }

    companion object {
        const val BATCH = 100
        const val LOW_WATER = 20
        private const val MAX_REGISTRATION_ID = 16380
        private const val MAX_KEY_ID = 0xFFFFFE
        private const val PREKEY_COUNTER = "next_prekey"
        private const val SIGNED_COUNTER = "next_signed"
        private const val KYBER_COUNTER = "next_kyber"

        internal fun b64(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

        internal fun unb64(text: String): ByteArray = Base64.getDecoder().decode(text)
    }
}

internal fun SignalStore.counter(name: String): Int? = stateDao.get(SignalStore.LOCAL, name)?.extra?.toInt()

internal fun SignalStore.setCounter(name: String, value: Int) =
    stateDao.put(SignalStateEntity(SignalStore.LOCAL, name, ByteArray(0), value.toLong()))
