package app.blesk.core.messaging

import app.blesk.core.crypto.phrase.PhraseKeys
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

    /** The server registers by invite and none came with the request. */
    data object InviteRequired : RegisterResult

    /** The invite is unknown, already used or expired. */
    data object InviteInvalid : RegisterResult

    /** Too many attempts from this address in a short time; a minute later it works again. */
    data object TooManyAttempts : RegisterResult
    data object Unreachable : RegisterResult
}

/** How opening an account with its phrase went. */
sealed interface RecoverResult {
    data class Ok(val username: String) : RecoverResult

    /** The words are a valid phrase, but no account on this server has it. */
    data object Unknown : RecoverResult
    data object Unreachable : RecoverResult
}

/**
 * The account on a server: created with a username and this device's identity key (no phone
 * number), signed in by signing a fresh challenge. The session token lives in the encrypted
 * database next to the keys.
 */
class AccountManager(private val store: SignalStore, private val keys: KeyManager) {

    /**
     * [phrase] binds the recovery phrase to the account as it is created; only the hash of its token
     * goes. [invite] is the code a server that registers by invite asks for; an open server ignores it.
     *
     * [resume] is set when an earlier attempt with this name and the same [phrase] broke off: the
     * server may have made that account and spent its invite before the connection dropped, so a
     * refusal for a taken name or a used invite first tries to sign in to it with this phone's key.
     */
    suspend fun register(
        api: BleskApi,
        username: String,
        phrase: PhraseKeys? = null,
        invite: String? = null,
        resume: Boolean = false,
    ): RegisterResult {
        if (!Usernames.isValid(username)) return RegisterResult.NameInvalid
        return try {
            api.info()
            keys.ensureIdentity()
            val refused = try {
                api.register(keys.registration(username, phrase?.confirmationHash, invite))
                null
            } catch (e: ApiException) {
                e.registerResult()
            }
            when {
                refused == null -> signIn(api, username)
                resume && refused in LEFT_BY_BROKEN_ATTEMPT && signedIn(api, username) -> Unit
                else -> return refused
            }
            api.uploadKeys(keys.initialUpload())
            RegisterResult.Ok
        } catch (e: ApiException) {
            e.registerResult()
        } catch (_: java.io.IOException) {
            RegisterResult.Unreachable
        }
    }

    /** Signs in if [username] is this phone's account; anyone else's refuses the signature of this identity key. */
    private suspend fun signedIn(api: BleskApi, username: String): Boolean = try {
        signIn(api, username)
        true
    } catch (e: ApiException) {
        if (e.code == "auth_failed") false else throw e
    }

    private fun ApiException.registerResult(): RegisterResult = when (code) {
        "username_taken" -> RegisterResult.NameTaken
        "invalid_username" -> RegisterResult.NameInvalid
        "invite_required" -> RegisterResult.InviteRequired
        "invite_invalid" -> RegisterResult.InviteInvalid
        "rate_limited" -> RegisterResult.TooManyAttempts
        else -> RegisterResult.Unreachable
    }

    /**
     * Takes over the account of [phrase] with this phone's fresh identity key; the phone that held
     * it before is signed out by the server.
     */
    suspend fun recover(api: BleskApi, phrase: PhraseKeys): RecoverResult = try {
        api.info()
        keys.ensureIdentity()
        val account = api.recover(keys.recovery(phrase.confirmation))
        signIn(api, account.username)
        api.uploadKeys(keys.initialUpload())
        RecoverResult.Ok(account.username)
    } catch (e: ApiException) {
        if (e.code == "auth_failed") RecoverResult.Unknown else RecoverResult.Unreachable
    } catch (_: java.io.IOException) {
        RecoverResult.Unreachable
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
        /** What the server answers to a second try of an account it has already made. */
        val LEFT_BY_BROKEN_ATTEMPT = setOf(RegisterResult.NameTaken, RegisterResult.InviteInvalid)
        const val TOKEN = "token"
        const val REFRESH_MARGIN_MILLIS = 24L * 60 * 60 * 1000
    }
}
