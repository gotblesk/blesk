package app.blesk

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.network.ServerAddress
import app.blesk.screenshots.FrozenApplication
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Nothing here opens the database: SQLCipher does not load under Robolectric, and choosing a stack needs none. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = FrozenApplication::class)
class ServerStacksTest {
    private val container = ApplicationProvider.getApplicationContext<BleskApplication>().container

    @Test
    fun aRetryOnTheSameServerKeepsItsStack() {
        val stack = container.stackFor(ServerAddress(OTHER_SERVER, PIN))
        assertSame(stack, container.stackFor(ServerAddress(OTHER_SERVER, PIN)))
    }

    @Test
    fun anotherServerSharesTheOneDatabase() {
        val other = container.stackFor(ServerAddress(OTHER_SERVER, PIN))
        val rescanned = container.stackFor(ServerAddress(OTHER_SERVER, ANOTHER_PIN))
        val blesk = container.stackFor(ServerAddress.DEFAULT)
        // Another key under the same address is another server: the pin checked on every connection is the new one.
        assertNotSame(other, rescanned)
        assertNotSame(rescanned, blesk)
        assertSame(other.vault, rescanned.vault)
        assertSame(other.vault, blesk.vault)
    }

    @Test
    fun aServersWorkEndsAloneAndItsFailuresReachTheJournal() {
        val work = container.childScope()
        val failures = { container.eventLog.entries.value.count { it.code == EventCode.BackgroundFailure } }
        val before = failures()

        work.launch { error("broken") }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(before + 1, failures())
        assertTrue(work.isActive)

        work.cancel()
        assertFalse(work.isActive)
        assertTrue(container.scope.isActive)
    }

    private companion object {
        const val OTHER_SERVER = "https://chat.example.ru"
        val PIN = "sha256/" + "A".repeat(43) + "="
        val ANOTHER_PIN = "sha256/" + "B".repeat(43) + "="
    }
}
