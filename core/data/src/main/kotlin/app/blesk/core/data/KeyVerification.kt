package app.blesk.core.data

import kotlinx.coroutines.flow.Flow

/** The 60 digits and the code to scan for one person. */
class SafetyCode(val digits: String, val scannable: String)

/** Where you stand with someone's key. */
data class KeyState(val verified: Boolean, val changed: Boolean)

/** Checking that nobody sits between you and the other person. */
interface KeyVerification {
    /** Null until you have exchanged at least one message. */
    suspend fun safetyCode(username: String): SafetyCode?

    fun keyState(username: String): Flow<KeyState>

    /** Marks verified if [scanned] (what the other phone shows) matches; false otherwise. */
    suspend fun verifyScanned(username: String, scanned: String): Boolean

    suspend fun setVerified(username: String, verified: Boolean)

    /** Told by the key store that [username]'s identity key is not the one we knew. */
    fun onKeyChanged(username: String)
}
