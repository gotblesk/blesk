package app.blesk.locks

import app.blesk.core.data.settings.LockRelock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which locked chats are open right now. Nothing here is stored: after the app restarts every
 * lock is closed. A chat closes again as its rules say — when you leave it, or a minute after — and
 * every one of them closes when the app goes to the background.
 */
class ChatLockSession(private val scope: CoroutineScope) {
    private val state = MutableStateFlow<Set<String>>(emptySet())
    private val timers = HashMap<String, Job>()

    val open: StateFlow<Set<String>> = state.asStateFlow()

    fun isOpen(id: String) = id in state.value

    fun opened(id: String) {
        synchronized(timers) { timers.remove(id)?.cancel() }
        state.update { it + id }
    }

    /** Leaving a chat (or the archive) that was opened. */
    fun left(id: String, relock: LockRelock) {
        when (relock) {
            LockRelock.OnExit -> close(id)
            LockRelock.AfterMinute -> synchronized(timers) {
                timers.remove(id)?.cancel()
                timers[id] = scope.launch {
                    delay(MINUTE)
                    close(id)
                }
            }
            LockRelock.OnBackground -> Unit
        }
    }

    fun onBackground() {
        synchronized(timers) {
            timers.values.forEach { it.cancel() }
            timers.clear()
        }
        state.value = emptySet()
    }

    /** A chat taken off the lock no longer needs to be remembered as open. */
    fun close(id: String) {
        synchronized(timers) { timers.remove(id)?.cancel() }
        state.update { it - id }
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
