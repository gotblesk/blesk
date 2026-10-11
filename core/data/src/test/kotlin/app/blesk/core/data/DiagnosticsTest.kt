package app.blesk.core.data

import app.blesk.core.data.diagnostics.CrashStore
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.diagnostics.EventLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DiagnosticsTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `event log keeps only the latest entries`() {
        val log = EventLog(capacity = 3, clock = { 0L })
        EventCode.entries.take(5).forEach { log.record(it) }
        assertEquals(EventCode.entries.subList(2, 5), log.entries.value.map { it.code })
    }

    @Test
    fun `crash report drops exception messages`() {
        val secret = "Привет, это личное сообщение"
        val error = IllegalStateException(secret, IllegalArgumentException("ключ abc"))
        val trace = CrashStore.sanitize(error)
        assertFalse(trace.contains(secret))
        assertFalse(trace.contains("ключ"))
        assertTrue(trace.startsWith("java.lang.IllegalStateException"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException"))
    }

    @Test
    fun `crash store writes reads and clears`() {
        val store = CrashStore(folder.root, clock = { 42L })
        assertNull(store.read())
        store.write(RuntimeException("x"))
        assertEquals(42L, store.read()?.atMillis)
        store.clear()
        assertNull(store.read())
    }
}
