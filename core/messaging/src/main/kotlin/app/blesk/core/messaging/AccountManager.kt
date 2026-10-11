package app.blesk.core.messaging

import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.SignalStateEntity
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.TokenRequest
import app.blesk.shared.protocol.Usernames

/** Why registration did not work, in terms the screen can explain. */
sealed interface RegisterResult {
    data object Ok : RegisterResult
    data object NameTaken : RegisterResult
    data object NameInvalid : RegisterResult
    data object Unreachable : RegisterResult
}

/**
 * The account on a server: created with a username and this device's identity key (no phone
 * number), signed in by signing a fresh challenge. The session token lives in the encrypted
 * database next to the keys.
 */
class AccountManager(private val store: SignalStore, private val keys: KeyManager) {

    suspend fun register(api: BleskApi, username: String): RegisterResult {
        if (!Usernames.isValid(username)) return RegisterResult.NameInvalid
        return try {
            api.info()
            keys.ensureIdentity()
            api.register(keys.registration(username))
            signIn(api, username)
            api.uploadKeys(keys.initialUpload())
            RegisterResult.Ok
        } catch (e: ApiException) {
            when (e.code) {
                "username_taken" -> RegisterResult.NameTaken
                "invalid_username" -> RegisterResult.NameInvalid
                else -> RegisterResult.Unreachable
            }
        } catch (_: java.io.IOException) {
            RegisterResult.Unreachable
        }
    }

    /** Signs in with the identity key and remembers the token. */
    suspend fun signIn(api: BleskApi, username: String) {
        val challenge = api.challenge(username)
        val token = api.token(TokenRequest(username, challenge.nonce, keys.signAuth(challenge.nonce)))
        store.stateDao.put(SignalStateEntity(SignalStore.LOCAL, TOKEN, token.token.encodeToByteArray(), token.expiresAtMillis))
        api.token = token.token
    }

    /** Puts the saved token on [api]; returns false if it is missing or expired. */
    fun restore(api: BleskApi, now: Long = System.currentTimeMillis()): Boolean {
        val saved = store.stateDao.get(SignalStore.LOCAL, TOKEN) ?: return false
        if (saved.extra <= now + REFRESH_MARGIN_MILLIS) return false
        api.token = saved.record.decodeToString()
        return true
    }

    private companion object {
        const val TOKEN = "token"
        const val REFRESH_MARGIN_MILLIS = 24L * 60 * 60 * 1000
    }
}
