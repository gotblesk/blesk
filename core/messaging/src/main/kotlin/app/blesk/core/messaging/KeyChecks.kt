package app.blesk.core.messaging

import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.KeyState
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.SafetyCode
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Base64

/** Safety numbers and the verified mark; the numbers themselves come from libsignal. */
internal class KeyChecks(
    private val db: BleskDatabase,
    private val crypto: MessageCrypto,
    private val scope: CoroutineScope,
    private val log: EventLog,
) : KeyVerification {
    override suspend fun safetyCode(username: String): SafetyCode? = withContext(Dispatchers.IO) {
        crypto.safetyNumber(username)?.let { SafetyCode(it.digits, Base64.getEncoder().encodeToString(it.scannable)) }
    }

    override fun keyState(username: String): Flow<KeyState> =
        db.contacts().observe(username).map { KeyState(verified = it?.verified == true, changed = it?.keyChanged == true) }

    override suspend fun verifyScanned(username: String, scanned: String): Boolean {
        val bytes = runCatching { Base64.getDecoder().decode(scanned.trim()) }.getOrNull() ?: return false
        val ok = withContext(Dispatchers.IO) { crypto.matches(username, bytes) }
        if (ok) setVerified(username, true)
        return ok
    }

    override suspend fun setVerified(username: String, verified: Boolean) = db.contacts().setVerified(username, verified)

    /** Called by the key store: someone's identity key is not the one we knew. */
    override fun onKeyChanged(username: String) {
        scope.launch {
            if (db.contacts().markKeyChanged(username) > 0) log.record(EventCode.KeyChanged)
        }
    }
}
