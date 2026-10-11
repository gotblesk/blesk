package app.blesk.server.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object Tokens {
    private val random = SecureRandom()
    private val urlSafe = Base64.getUrlEncoder().withoutPadding()

    /** URL-safe because the socket may receive the token in a query parameter. */
    fun newToken(): String = urlSafe.encodeToString(randomBytes(TOKEN_BYTES))

    fun newNonce(): String = Base64.getEncoder().encodeToString(randomBytes(TOKEN_BYTES))

    /** 128 random bits, URL-safe, used as attachment ids. */
    fun newId(): String = urlSafe.encodeToString(randomBytes(ID_BYTES))

    fun hash(token: String): ByteArray = MessageDigest.getInstance("SHA-256").digest(token.encodeToByteArray())

    private fun randomBytes(count: Int) = ByteArray(count).also(random::nextBytes)

    private const val TOKEN_BYTES = 32
    private const val ID_BYTES = 16
}
