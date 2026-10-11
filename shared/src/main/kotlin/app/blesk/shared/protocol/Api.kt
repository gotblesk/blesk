package app.blesk.shared.protocol

import kotlinx.serialization.Serializable

/**
 * The client–server contract, version 1. Byte fields are standard Base64. The server stores
 * only public keys and ciphertext; it never sees message content, profiles or contacts.
 * Every account has exactly one device in v1 ([DEVICE_ID]).
 */
object Api {
    const val VERSION = 1
    const val DEVICE_ID = 1

    const val ACCOUNTS = "/v1/accounts"
    const val CHALLENGE = "/v1/auth/challenge"
    const val TOKEN = "/v1/auth/token"
    const val KEYS = "/v1/keys"
    const val KEY_STATUS = "/v1/keys/status"
    const val MESSAGES = "/v1/messages"
    const val ATTACHMENTS = "/v1/attachments"
    const val SOCKET = "/v1/ws"
    const val INFO = "/v1/info"

    /** Signed by the identity key when registering, so nobody can claim a name with someone else's key. */
    fun registrationProof(username: String) = "blesk-register:$username".encodeToByteArray()

    /** Signed by the identity key to log in. */
    fun authProof(nonce: String) = "blesk-auth:$nonce".encodeToByteArray()

    /** Max size of an encrypted attachment upload. */
    const val MAX_ATTACHMENT_BYTES = 210L * 1024 * 1024

    /** Max size of one encrypted message body. */
    const val MAX_MESSAGE_BYTES = 256 * 1024
}

/** Lowercase latin letters, digits and underscores, 3 to 32 characters. */
object Usernames {
    private val pattern = Regex("^[a-z0-9_]{3,32}$")

    fun isValid(username: String) = pattern.matches(username)
}

@Serializable
data class ServerInfo(val name: String = "blesk", val version: Int = Api.VERSION)

@Serializable
data class RegisterRequest(
    val username: String,
    val identityKey: String,
    val registrationId: Int,
    /** Signature of [Api.registrationProof] by the identity key. */
    val signature: String,
)

@Serializable
data class AccountInfo(val username: String, val identityKey: String)

@Serializable
data class ChallengeRequest(val username: String)

@Serializable
data class Challenge(val nonce: String, val expiresAtMillis: Long)

@Serializable
data class TokenRequest(val username: String, val nonce: String, val signature: String)

@Serializable
data class TokenResponse(val token: String, val expiresAtMillis: Long)

@Serializable
data class SignedKey(val id: Int, val publicKey: String, val signature: String)

@Serializable
data class OneTimeKey(val id: Int, val publicKey: String)

/** Uploaded by the owner. Signed and last-resort keys replace the old ones; one-time keys add up. */
@Serializable
data class KeyUpload(
    val signedPreKey: SignedKey? = null,
    val kyberLastResort: SignedKey? = null,
    val preKeys: List<OneTimeKey> = emptyList(),
    val kyberPreKeys: List<SignedKey> = emptyList(),
)

@Serializable
data class KeyStatus(val preKeys: Int, val kyberPreKeys: Int)

/** What a sender needs to start a PQXDH session. One-time keys are handed out once. */
@Serializable
data class PreKeyBundleDto(
    val username: String,
    val registrationId: Int,
    val deviceId: Int,
    val identityKey: String,
    val signedPreKey: SignedKey,
    val preKey: OneTimeKey? = null,
    /** A one-time Kyber key if any are left, otherwise the last-resort one. */
    val kyberPreKey: SignedKey,
)

/** libsignal ciphertext types as carried on the wire. */
object EnvelopeType {
    const val PREKEY = 3
    const val WHISPER = 2

    /** A group message encrypted with the sender's SenderKey. */
    const val SENDERKEY = 7
}

@Serializable
data class OutgoingMessage(
    /** Chosen by the sender; resending the same id is ignored, which makes retries safe. */
    val clientId: String,
    val type: Int,
    val body: String,
    /** Not stored when the recipient is offline (typing indicators). */
    val ephemeral: Boolean = false,
)

@Serializable
data class SendResult(val serverId: Long, val timestampMillis: Long)

@Serializable
data class Envelope(
    val serverId: Long,
    val sender: String,
    val type: Int,
    val body: String,
    val timestampMillis: Long,
)

@Serializable
data class AttachmentUploaded(val id: String)

@Serializable
data class ErrorBody(val code: String, val message: String = "")
