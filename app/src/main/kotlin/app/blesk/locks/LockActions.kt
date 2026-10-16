package app.blesk.locks

import app.blesk.AppContainer
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockListing
import app.blesk.core.data.settings.LockRules
import kotlinx.coroutines.launch

/** Whether there is a code to open locks with; without one a lock would guard nothing. */
val AppContainer.hasLockCode: Boolean get() = pinVault.isSet || lockVault.isSet

/**
 * Puts [id] under a lock or takes it off. With no code yet, the first lock asks for one instead:
 * [onNeedCode] leads to making it, and the chat is locked once it exists.
 */
fun AppContainer.setLocked(id: String, lock: Boolean, onNeedCode: () -> Unit) {
    if (lock && !hasLockCode) {
        pendingLock.value = id
        onNeedCode()
        return
    }
    scope.launch {
        chatLocks.setLocked(id, lock)
        if (!lock) lockSession.close(id)
    }
}

/** Called once a code exists: the lock that was waiting for it goes on. */
fun AppContainer.applyPendingLock() {
    val id = pendingLock.value ?: return
    pendingLock.value = null
    if (hasLockCode) scope.launch { chatLocks.setLocked(id, true) }
}

/**
 * Whether a chat stays out of a listing — search, or «Поделиться» and shortcuts — under its lock's
 * rules, or the archive's when it lies in a locked archive.
 */
fun ChatLocks.hides(conversationId: String, archived: Boolean, listing: (LockRules) -> LockListing): Boolean {
    val lock = lockOf(conversationId) ?: ChatLocks.ARCHIVE.takeIf { archived && isLocked(it) } ?: return false
    return listing(rulesFor(lock)) == LockListing.Hide
}
