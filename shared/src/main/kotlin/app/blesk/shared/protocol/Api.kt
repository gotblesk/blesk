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

    /** `GET /v1/usernames/{name}`: whether a name may be taken, asked while it is typed. */
    const val USERNAMES = "/v1/usernames"
    const val CHALLENGE = "/v1/auth/challenge"
    const val TOKEN = "/v1/auth/token"
    const val KEYS = "/v1/keys"
    const val KEY_STATUS = "/v1/keys/status"
    const val MESSAGES = "/v1/messages"
    const val ATTACHMENTS = "/v1/attachments"
    const val SOCKET = "/v1/ws"
    const val INFO = "/v1/info"
    const val DISCOVERY = "/v1/discovery"

    /** The signed-in account itself; DELETE removes it with its keys and waiting messages. */
    const val ACCOUNT = "/v1/account"

    /** The hash of the token that proves the recovery phrase is known. */
    const val RECOVERY = "/v1/account/recovery"

    /** Opens the account on a new phone with the token from the recovery phrase. */
    const val RECOVER = "/v1/account/recover"

    /** Query parameter of a lookup made from someone's link or QR code; see [linkKey]. */
    const val LINK = "link"

    /**
     * The key in a profile link or QR code that finds an account hidden from search: a short
     * digest of its public identity key. Whoever already talks to the account has that key too.
     */
    fun linkKey(identityKey: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(identityKey)
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(digest.copyOf(LINK_KEY_BYTES))
    }

    private const val LINK_KEY_BYTES = 12

    /** Signed by the identity key when registering, so nobody can claim a name with someone else's key. */
    fun registrationProof(username: String) = "blesk-register:$username".encodeToByteArray()

    /** Signed by the identity key to log in. */
    fun authProof(nonce: String) = "blesk-auth:$nonce".encodeToByteArray()

    /** Signed by the new phone's identity key when it takes the account over with the recovery phrase. */
    fun recoveryProof(token: String) = "blesk-recover:$token".encodeToByteArray()

    /** Max size of an encrypted attachment sent in one request. */
    const val MAX_ATTACHMENT_BYTES = 210L * 1024 * 1024

    /** Max size of an encrypted attachment uploaded in chunks: 2 GiB of file plus the encryption overhead. */
    const val MAX_CHUNKED_BYTES = 2L * 1024 * 1024 * 1024 + 64L * 1024 * 1024

    /** Every chunk but the last is exactly this long; a lost connection costs at most one chunk. */
    const val CHUNK_BYTES = 4 * 1024 * 1024

    /** Starts a chunked upload: `POST` with [UploadStart], then `PUT {id}/chunks/{n}`, then `POST {id}/complete`. */
    const val UPLOADS = "/v1/attachments/uploads"

    /** Max size of one encrypted message body. */
    const val MAX_MESSAGE_BYTES = 256 * 1024
}

/** The full size of the encrypted file about to be uploaded in chunks. */
@Serializable
data class UploadStart(val size: Long)

/** Where the chunks of a new upload go, and how long each is. */
@Serializable
data class UploadStarted(val id: String, val chunkBytes: Int)

/** Which chunks the server already has, so an interrupted upload continues where it stopped. */
@Serializable
data class UploadState(val received: List<Int>, val complete: Boolean)

/** Whether the account may be found by its username; hidden accounts open only from a link or QR code. */
@Serializable
data class Discovery(val discoverable: Boolean)

/** Lowercase latin letters, digits and underscores, 3 to 32 characters. */
/**
 * @username: lower-case latin letters, digits, dot and underscore, 3 to 32 of them, starting with
 * a letter; a dot or underscore never at either end and never two in a row. Latin only, so a
 * look-alike Cyrillic letter cannot pass for someone else. Lower case only, so «Egor» and «egor»
 * are one name. Names of the service itself are kept back.
 */
object Usernames {
    const val MIN = 3
    const val MAX = 32
    private val pattern = Regex("^[a-z][a-z0-9]*(?:[._][a-z0-9]+)*$")
    private val reserved = setOf(
        "admin", "administrator", "support", "blesk", "system", "moderator", "mod", "root", "help",
        "official", "security", "staff", "service", "owner", "team", "info", "abuse", "noreply",
    )

    fun isValid(username: String) = username.length in MIN..MAX && pattern.matches(username) && !isReserved(username)

    /** «b.lesk» and «blesk_» are as reserved as «blesk». */
    fun isReserved(username: String) = username.filter { it != '.' && it != '_' } in reserved

    /** What is typed, as a username can be: lower case, without «@» and anything not allowed. */
    fun normalize(raw: String): String =
        raw.trim().removePrefix("@").lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '.' || it == '_' }.take(MAX)
}

@Serializable
data class ServerInfo(
    val name: String = "blesk",
    val version: Int = Api.VERSION,
    /** Whether a LiveKit server is set up next to this one. */
    val calls: Boolean = false,
)

/** The answer to «is this @username free?»: invalid and reserved names are not free either. */
@Serializable
data class UsernameCheck(val available: Boolean)

@Serializable
data class RegisterRequest(
    val username: String,
    val identityKey: String,
    val registrationId: Int,
    /** Signature of [Api.registrationProof] by the identity key. */
    val signature: String,
    /** Base64 SHA-256 of the recovery phrase's confirmation token, stored with the account from the start. */
    val recoveryHash: String? = null,
)

@Serializable
data class AccountInfo(val username: String, val identityKey: String)

/** SHA-256 of the confirmation token, in Base64; the token itself never leaves the phone except to recover. */
@Serializable
data class RecoveryHash(val hash: String)

/**
 * A new phone taking the account over: the confirmation token from the phrase, and the phone's own
 * identity key with [signature] of [Api.recoveryProof] by it.
 */
@Serializable
data class RecoverRequest(
    val token: String,
    val identityKey: String,
    val registrationId: Int,
    val signature: String,
)

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

    /** From a bot: NOT encrypted, the body is a [BotMessage] in JSON. */
    const val BOT = 100
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
