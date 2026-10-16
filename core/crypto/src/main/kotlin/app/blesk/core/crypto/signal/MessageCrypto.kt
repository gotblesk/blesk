package app.blesk.core.crypto.signal

import app.blesk.core.crypto.signal.KeyManager.Companion.b64
import app.blesk.core.crypto.signal.KeyManager.Companion.unb64
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.PreKeyBundleDto
import kotlinx.serialization.json.Json
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.fingerprint.FingerprintParsingException
import org.signal.libsignal.protocol.fingerprint.FingerprintVersionMismatchException
import org.signal.libsignal.protocol.fingerprint.NumericFingerprintGenerator
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle

/** 60 digits to read aloud and a code to scan, for checking each other's keys. */
class SafetyNumber(val digits: String, val scannable: ByteArray)

/** A Signal-encrypted message ready for the wire. */
data class Sealed(val type: Int, val body: String)

/**
 * One-to-one end-to-end encryption: PQXDH to start a session, the Double Ratchet after.
 * All cryptography is libsignal's; this class only moves bytes between it and the wire format.
 */
class MessageCrypto(private val store: SignalStore, private val localUsername: () -> String) {
    private val json = Json { ignoreUnknownKeys = true }

    fun hasSession(username: String): Boolean = synchronized(store.lock) { store.containsSession(address(username)) }

    /** Starts a session from someone's published keys. Verifies their signatures inside libsignal. */
    fun startSession(bundle: PreKeyBundleDto) {
        val preKey = bundle.preKey
        val signal = PreKeyBundle(
            bundle.registrationId,
            bundle.deviceId,
            preKey?.id ?: PreKeyBundle.NULL_PRE_KEY_ID,
            preKey?.let { ECPublicKey(unb64(it.publicKey)) },
            bundle.signedPreKey.id,
            ECPublicKey(unb64(bundle.signedPreKey.publicKey)),
            unb64(bundle.signedPreKey.signature),
            IdentityKey(unb64(bundle.identityKey)),
            bundle.kyberPreKey.id,
            KEMPublicKey(unb64(bundle.kyberPreKey.publicKey)),
            unb64(bundle.kyberPreKey.signature),
        )
        synchronized(store.lock) { SessionBuilder(store, address(bundle.username), local()).process(signal) }
    }

    fun encrypt(username: String, content: Content): Sealed {
        val plain = Padding.pad(json.encodeToString(Content.serializer(), content).encodeToByteArray())
        val message = synchronized(store.lock) { SessionCipher(store, local(), address(username)).encrypt(plain) }
        return Sealed(message.type, b64(message.serialize()))
    }

    /** Decrypts an envelope; throws libsignal exceptions for anything forged, replayed or broken. */
    fun decrypt(envelope: Envelope): Content {
        val cipher = SessionCipher(store, local(), address(envelope.sender))
        val bytes = unb64(envelope.body)
        val plain = synchronized(store.lock) {
            when (envelope.type) {
                EnvelopeType.PREKEY -> cipher.decrypt(PreKeySignalMessage(bytes))
                EnvelopeType.WHISPER -> cipher.decrypt(SignalMessage(bytes))
                else -> throw IllegalArgumentException("Unknown envelope type ${envelope.type}")
            }
        }
        return json.decodeFromString(Content.serializer(), Padding.unpad(plain).decodeToString())
    }

    fun remoteIdentity(username: String): IdentityKey? = store.getIdentity(address(username))

    /**
     * The safety number with [username]: the same 60 digits on both phones when nobody sits in
     * the middle. Null until there is a session. Computed by libsignal, as in Signal.
     */
    fun safetyNumber(username: String): SafetyNumber? {
        val remote = remoteIdentity(username) ?: return null
        val fingerprint = NumericFingerprintGenerator(FINGERPRINT_ITERATIONS).createFor(
            FINGERPRINT_VERSION,
            localUsername().encodeToByteArray(),
            store.identityKeyPair.publicKey,
            username.encodeToByteArray(),
            remote,
        )
        return SafetyNumber(fingerprint.displayableFingerprint.displayText, fingerprint.scannableFingerprint.serialized)
    }

    /** Whether a code scanned from the other phone matches ours; false for anything malformed. */
    fun matches(username: String, scanned: ByteArray): Boolean {
        val remote = remoteIdentity(username) ?: return false
        val ours = NumericFingerprintGenerator(FINGERPRINT_ITERATIONS).createFor(
            FINGERPRINT_VERSION,
            localUsername().encodeToByteArray(),
            store.identityKeyPair.publicKey,
            username.encodeToByteArray(),
            remote,
        )
        return try {
            ours.scannableFingerprint.compareTo(scanned)
        } catch (_: FingerprintVersionMismatchException) {
            false
        } catch (_: FingerprintParsingException) {
            false
        }
    }

    private fun address(username: String) = SignalProtocolAddress(username, Api.DEVICE_ID)

    private fun local() = address(localUsername())

    private companion object {
        /** Signal's values: version 2 of the scannable format, 5200 hash iterations. */
        const val FINGERPRINT_VERSION = 2
        const val FINGERPRINT_ITERATIONS = 5200
    }
}

/**
 * Pads plaintext to a multiple of [BLOCK] bytes (a 0x80 marker, then zeros) so ciphertext
 * length says little about message length.
 */
internal object Padding {
    private const val BLOCK = 160
    private const val MARKER: Byte = 0x80.toByte()

    fun pad(data: ByteArray): ByteArray {
        val size = (data.size / BLOCK + 1) * BLOCK
        return data.copyOf(size).also { it[data.size] = MARKER }
    }

    fun unpad(data: ByteArray): ByteArray {
        var end = data.size - 1
        while (end >= 0 && data[end] == 0.toByte()) end--
        require(end >= 0 && data[end] == MARKER) { "Bad padding" }
        return data.copyOf(end)
    }
}
