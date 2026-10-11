package app.blesk.server.auth

import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.InvalidKeyException

/** Signature checks are delegated to libsignal so the server verifies exactly what clients sign. */
object Signatures {
    /** Null when [serialized] is not a valid identity key. */
    fun identityKey(serialized: ByteArray): IdentityKey? = try {
        IdentityKey(serialized)
    } catch (_: InvalidKeyException) {
        null
    }

    fun verify(identityKey: IdentityKey, message: ByteArray, signature: ByteArray): Boolean =
        identityKey.publicKey.verifySignature(message, signature)
}
