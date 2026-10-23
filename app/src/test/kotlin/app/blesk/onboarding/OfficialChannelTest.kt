package app.blesk.onboarding

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.Connection
import app.blesk.core.data.settings.OnboardingStore
import app.blesk.core.data.settings.SettingsStore
import app.blesk.shared.protocol.ServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/** The official channel of an open account: what its server names is kept, and the subscription asked for is made. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], application = Application::class)
class OfficialChannelTest {
    private val store =
        OnboardingStore(SettingsStore(ApplicationProvider.getApplicationContext(), "settings-${UUID.randomUUID()}"))

    // A shared flow, not a state: a real connection may say «online» twice in a row.
    private val connection = MutableSharedFlow<Connection>(replay = 1)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val joins = Channel<String>(Channel.UNLIMITED)

    /** What the server answers to each join, in turn; it takes every one after these. */
    private val answers = ConcurrentLinkedQueue<Boolean>()
    private val looks = AtomicInteger()

    @After
    fun stop() {
        scope.cancel()
    }

    private val join: suspend (String) -> Boolean = { handle ->
        joins.send(handle)
        answers.poll() ?: true
    }

    private fun follow(info: ServerInfo? = ServerInfo()) {
        scope.launch { followOfficialChannel(store, connection, info = { info.also { looks.incrementAndGet() } }, join = join) }
    }

    @Test
    fun theChannelTheServerNamesIsKeptOnceOnline() = runBlocking {
        connection.emit(Connection.Connecting)
        follow(ServerInfo(officialChannel = "blesk"))
        delay(SETTLE_MILLIS)
        assertEquals(0, looks.get())

        connection.emit(Connection.Online)

        eventually("blesk") { store.officialChannel.first() }
        assertEquals(1, looks.get())
    }

    @Test
    fun aServerThatNamesNoChannelLeavesNone() = runBlocking {
        store.setOfficialChannel("blesk")
        connection.emit(Connection.Online)

        follow(ServerInfo(officialChannel = null))

        eventually(null) { store.officialChannel.first() }
    }

    @Test
    fun aServerThatDidNotAnswerKeepsTheChannelKnown() = runBlocking {
        store.setOfficialChannel("blesk")
        store.finishSetup(subscribeTo = "blesk")
        connection.emit(Connection.Online)

        follow(info = null)

        // The join comes after the look: by then the look has had its say.
        withTimeout(WAIT_MILLIS) { joins.receive() }
        assertEquals("blesk", store.officialChannel.first())
    }

    @Test
    fun aSubscriptionLeftWaitingIsMadeOnceOnline() = runBlocking {
        store.finishSetup(subscribeTo = "blesk")
        connection.emit(Connection.Connecting)
        follow()
        delay(SETTLE_MILLIS)
        assertTrue(joins.tryReceive().isFailure)

        connection.emit(Connection.Online)

        assertEquals("blesk", withTimeout(WAIT_MILLIS) { joins.receive() })
        eventually(null) { store.subscribePending.first() }
    }

    @Test
    fun nothingIsJoinedUnasked() = runBlocking {
        connection.emit(Connection.Online)

        follow(ServerInfo(officialChannel = "blesk"))

        eventually("blesk") { store.officialChannel.first() }
        delay(SETTLE_MILLIS)
        assertTrue(joins.tryReceive().isFailure)
    }

    @Test
    fun aJoinTheServerDidNotTakeIsTriedAtTheNextConnection() = runBlocking {
        answers += false
        store.finishSetup(subscribeTo = "blesk")
        connection.emit(Connection.Online)
        scope.launch { subscribeToOfficialChannel("blesk", store, connection, join) }
        withTimeout(WAIT_MILLIS) { joins.receive() }

        connection.emit(Connection.Online)
        delay(SETTLE_MILLIS)
        assertTrue("tried again without a new connection", joins.tryReceive().isFailure)
        assertEquals("blesk", store.subscribePending.first())

        connection.emit(Connection.Connecting)
        connection.emit(Connection.Online)

        withTimeout(WAIT_MILLIS) { joins.receive() }
        eventually(null) { store.subscribePending.first() }
    }

    /**
     * Reads until [read] gives [expected]. A fresh settings file may hand a reader that starts at the moment of
     * a write the value before it, and nothing after; a new read gets the one written.
     */
    private suspend fun <T> eventually(expected: T, read: suspend () -> T) {
        withTimeout(WAIT_MILLIS) { while (read() != expected) delay(POLL_MILLIS) }
    }

    private companion object {
        const val WAIT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 300L
        const val POLL_MILLIS = 10L
    }
}
