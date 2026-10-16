package app.blesk.locks

import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockListing
import app.blesk.core.data.settings.LockOverrides
import app.blesk.core.data.settings.LockRelock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatLockSessionTest {
    @Test
    fun closesAsTheRulesSay() {
        val scope = TestScope()
        val session = ChatLockSession(scope)
        session.opened("a")
        session.left("a", LockRelock.OnExit)
        assertFalse(session.isOpen("a"))

        session.opened("b")
        session.left("b", LockRelock.AfterMinute)
        scope.advanceTimeBy(59_000)
        assertTrue(session.isOpen("b"))
        scope.advanceTimeBy(2_000)
        scope.runCurrent()
        assertFalse(session.isOpen("b"))

        session.opened("c")
        session.left("c", LockRelock.OnBackground)
        assertTrue(session.isOpen("c"))
        session.onBackground()
        assertFalse(session.isOpen("c"))
    }

    @Test
    fun comingBackWithinTheMinuteKeepsItOpen() {
        val scope = TestScope()
        val session = ChatLockSession(scope)
        session.opened("a")
        session.left("a", LockRelock.AfterMinute)
        scope.advanceTimeBy(30_000)
        session.opened("a")
        scope.advanceTimeBy(60_000)
        scope.runCurrent()
        assertTrue(session.isOpen("a"))
    }

    @Test
    fun lockOfFindsTheRoomForItsChannel() {
        val locks = ChatLocks(locked = setOf("room"))
        assertEquals("room", locks.lockOf("room:general"))
        assertNull(locks.lockOf("lera"))
    }

    @Test
    fun hiddenFromListingsAsTheLockSays() {
        val locks = ChatLocks(
            locked = setOf("anya", ChatLocks.ARCHIVE),
            overrides = mapOf("anya" to LockOverrides(search = LockListing.Show)),
        )
        assertFalse(locks.hides("anya", archived = false) { it.search })
        assertTrue(locks.hides("anya", archived = false) { it.share })
        assertTrue(locks.hides("lera", archived = true) { it.search })
        assertFalse(locks.hides("lera", archived = false) { it.search })
    }
}
