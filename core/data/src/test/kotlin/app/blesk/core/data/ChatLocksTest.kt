package app.blesk.core.data

import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockCounter
import app.blesk.core.data.settings.LockNotify
import app.blesk.core.data.settings.LockOverrides
import app.blesk.core.data.settings.LockRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLocksTest {
    private val locks = ChatLocks(
        rules = LockRules(),
        locked = setOf("anya", "room", ChatLocks.ARCHIVE),
        overrides = mapOf("anya" to LockOverrides(counter = LockCounter.Number, notify = LockNotify.Full)),
    )

    @Test
    fun defaultsAreTheBriefs() {
        val rules = LockRules()
        assertEquals(LockCounter.Dot, rules.counter)
        assertEquals(LockNotify.Anonymous, rules.notify)
    }

    @Test
    fun aChatsOwnRulesWinOnlyWhereSet() {
        val anya = locks.rulesFor("anya")
        assertEquals(LockCounter.Number, anya.counter)
        assertEquals(LockNotify.Full, anya.notify)
        assertEquals(LockRules().preview, anya.preview)
        assertEquals(LockRules(), locks.rulesFor("someone"))
    }

    @Test
    fun aRoomsLockCoversItsChannels() {
        assertTrue(locks.covers("room:general"))
        assertFalse(locks.covers("lera"))
    }

    @Test
    fun forgettingChatsKeepsTheRulesAndTheArchive() {
        val rules = LockRules(counter = LockCounter.Number)
        val archive = LockOverrides(notify = LockNotify.None)
        val all = locks.copy(rules = rules, overrides = locks.overrides + (ChatLocks.ARCHIVE to archive))

        val forgotten = all.withoutChats()

        assertEquals(ChatLocks(rules, setOf(ChatLocks.ARCHIVE), mapOf(ChatLocks.ARCHIVE to archive)), forgotten)
        assertEquals(ChatLocks(rules = rules), ChatLocks(rules = rules, locked = setOf("anya")).withoutChats())
    }

    @Test
    fun emptyOverridesAreNothing() {
        assertTrue(LockOverrides().isEmpty)
        assertFalse(LockOverrides(counter = LockCounter.Hidden).isEmpty)
    }
}
