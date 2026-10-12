package app.blesk.core.design.component

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NoticesTest {

    @Test
    fun actionRunsOnlyAfterTheUndoWindow() = runTest {
        val notices = Notices(backgroundScope)
        var done = 0
        notices.undoable("Удалено", hidden = listOf("m1")) { done++ }
        assertTrue("m1" in notices.hidden)
        advanceTimeBy(4_900)
        assertEquals(0, done)
        advanceTimeBy(200)
        runCurrent()
        assertEquals(1, done)
        assertTrue(notices.hidden.isEmpty())
        assertNull(notices.current)
    }

    @Test
    fun undoKeepsEverything() = runTest {
        val notices = Notices(backgroundScope)
        var done = 0
        notices.undoable("Удалено", hidden = listOf("m1")) { done++ }
        notices.undo()
        advanceTimeBy(10_000)
        assertEquals(0, done)
        assertTrue(notices.hidden.isEmpty())
    }

    @Test
    fun aNewNoticeCarriesThePreviousActionOut() = runTest {
        val notices = Notices(backgroundScope)
        var done = 0
        notices.undoable("Удалено", hidden = listOf("m1")) { done++ }
        notices.show("Скопировано")
        assertEquals(1, done)
        assertEquals("Скопировано", notices.current?.text)
    }
}
