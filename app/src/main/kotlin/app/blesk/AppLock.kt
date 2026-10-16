package app.blesk

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blesk.core.crypto.PinVault

/**
 * Decides when the PIN is asked: on start, and when the app returns after staying in the
 * background longer than the chosen delay. Trips to our own pickers and system dialogs are
 * announced with [expectReturn] and do not lock.
 */
class AppLock(private val vault: PinVault, private val clock: () -> Long = System::currentTimeMillis) {
    var locked by mutableStateOf(vault.isSet)
        private set
    private var backgroundedAt = 0L
    private var returning = false

    /** A trip to a picker or system dialog is under way; leaving for it is not going to the background. */
    val expectingReturn: Boolean get() = returning

    fun expectReturn() {
        returning = true
    }

    fun onBackground() {
        backgroundedAt = clock()
    }

    fun onForeground(delayMillis: Long) {
        val wasAway = backgroundedAt > 0 && !returning
        if (wasAway && vault.isSet && clock() - backgroundedAt >= delayMillis) locked = true
        returning = false
        backgroundedAt = 0
    }

    fun unlock() {
        locked = false
    }
}
