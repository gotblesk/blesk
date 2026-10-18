package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.ServerFrame
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * When the outbox makes a pass and when it waits for the time the server named. Retry timers run on a test
 * clock: none fires unless a test moves it, so whatever goes out came from a pass someone asked for.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class OutboxTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = FakeServer()
    private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
    private val store = SignalStore(db.signal())
    private val accounts = AccountManager(store, KeyManager(store))
    private val api = server.api()
    private val virtualTime = TestCoroutineScheduler()
    private val timers = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(virtualTime))
    private val outbox = Outbox(
        db = db,
        api = api,
        crypto = MessageCrypto(store) { ME },
        accounts = accounts,
        me = ME,
        scope = timers,
        log = EventLog(),
        clock = System::currentTimeMillis,
        retryMillis = RETRY_MILLIS,
        shareProfile = {},
        linkOf = { null },
    )

    @Before
    fun setUp() = runBlocking { assertEquals(RegisterResult.Ok, accounts.register(api, ME)) }

    @After
    fun tearDown() {
        timers.cancel()
        db.close()
    }

    /** Queues a message already sealed, as a group send does, so no session is needed. */
    private suspend fun queue(text: String) =
        outbox.queueSealed(BOB, Content(text, 1, Body.Text(text)), EnvelopeType.SENDERKEY, text)

    private fun sentToBob(): List<String> =
        server.transport(BOB).frames.replayCache.filterIsInstance<ServerFrame.Deliver>().map { it.envelope.body }

    private suspend fun waiting(): List<Pair<String, Int>> = db.outbox().all().map { it.sealedBody.orEmpty() to it.attempts }

    private suspend fun untilARefusalIsHeld() = withTimeout(TIMEOUT) {
        while (server.refusalsHeld.get() == 0) delay(POLL)
    }

    @Test
    fun aMessageQueuedWhileAPassIsRefusedGoesRightAfterIt() = runBlocking {
        val answer = CompletableDeferred<Unit>()
        server.refuseSends(ME, FakeServer.Refusal(HttpStatusCode.ServiceUnavailable, retryAfterSeconds = 600, answerAfter = answer))
        queue("первое")
        val refused = outbox.flush()
        untilARefusalIsHeld()
        // The pass waits for the server's answer: this send finds the outbox busy and can only ask for another pass.
        queue("второе")
        outbox.flush()
        answer.complete(Unit)
        refused.join()

        assertEquals(listOf("первое", "второе"), sentToBob())
        assertEquals(emptyList<Pair<String, Int>>(), waiting())
    }

    @Test
    fun aPassGoesOnWhenWhoeverAskedForItIsGone() = runBlocking {
        val answer = CompletableDeferred<Unit>()
        server.refuseSends(ME, FakeServer.Refusal(HttpStatusCode.ServiceUnavailable, retryAfterSeconds = 600, answerAfter = answer))
        queue("первое")
        // A group change made on a screen asks for a pass and waits for it there.
        val screen = launch(Dispatchers.IO) { outbox.flush().join() }
        untilARefusalIsHeld()
        // A receipt is queued meanwhile and finds the outbox busy; then the screen is closed.
        queue("второе")
        outbox.flush()
        screen.cancelAndJoin()
        answer.complete(Unit)

        withTimeout(TIMEOUT) { while (waiting().isNotEmpty()) delay(POLL) }
        assertEquals(listOf("первое", "второе"), sentToBob())
    }

    @Test
    fun aRefusedPassAsksAgainOnlyWhenTheServerSaid() = runBlocking {
        server.refuseSends(ME, FakeServer.Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 600))
        queue("первое")
        outbox.flush().join()
        assertEquals(listOf("первое" to 1), waiting())

        virtualTime.advanceTimeBy(600_000L - 1)
        assertEquals(listOf("первое" to 1), waiting())
        virtualTime.advanceTimeBy(2)
        withTimeout(TIMEOUT) { while (waiting().isNotEmpty()) delay(POLL) }
        assertEquals(listOf("первое"), sentToBob())
    }

    @Test
    fun aRetryDueDuringAPassAddsNoTryAfterItsRefusal() = runBlocking {
        val answer = CompletableDeferred<Unit>()
        server.refuseSends(
            ME,
            FakeServer.Refusal(status = null),
            FakeServer.Refusal(HttpStatusCode.ServiceUnavailable, retryAfterSeconds = 600, answerAfter = answer),
        )
        queue("первое")
        // The connection drops: a retry is set for the usual pause.
        outbox.flush().join()
        queue("второе")
        val refused = outbox.flush()
        untilARefusalIsHeld()
        // The retry comes due while that pass waits for its answer, and the answer is then a refusal for ten minutes.
        virtualTime.advanceTimeBy(RETRY_MILLIS + 1)
        answer.complete(Unit)
        refused.join()

        assertEquals(listOf("первое" to 2, "второе" to 0), waiting())
        assertEquals(emptyList<String>(), sentToBob())
    }

    private companion object {
        const val ME = "alice"
        const val BOB = "bob"
        const val RETRY_MILLIS = 15_000L
        const val TIMEOUT = 60_000L
        const val POLL = 20L
    }
}
