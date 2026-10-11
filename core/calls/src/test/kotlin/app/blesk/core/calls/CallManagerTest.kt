package app.blesk.core.calls

import app.blesk.core.data.calls.CallOutcome
import app.blesk.core.data.calls.CallPhase
import app.blesk.core.data.calls.CallSignal
import app.blesk.core.data.calls.CallSignaling
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.CallToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CallManagerTest {

    /** Joins every phone in one shared "room" so presence works like LiveKit's. */
    private class FakeRoom {
        val inside = MutableStateFlow<Set<String>>(emptySet())
        val keys = mutableMapOf<String, String>()
    }

    private class FakeMedia(private val me: String, private val room: FakeRoom, scope: CoroutineScope) : MediaSession {
        private val mutable = MutableStateFlow(MediaState())
        override val state: StateFlow<MediaState> = mutable
        override var onDisconnected: () -> Unit = {}
        var joinedWith: String? = null

        init {
            scope.launchCollect(room.inside) { inside ->
                mutable.value = mutable.value.copy(othersJoined = (inside - me).isNotEmpty() && me in inside)
            }
        }

        override suspend fun connect(url: String, token: String, key: String, video: Boolean, http: OkHttpClient?): Boolean {
            joinedWith = key
            room.keys[me] = key
            mutable.value = mutable.value.copy(connected = true)
            room.inside.value = room.inside.value + me
            return true
        }

        override fun disconnect() {
            mutable.value = MediaState()
            room.inside.value = room.inside.value - me
        }
    }

    private class FakeSignaling(override val me: String, private val clock: () -> Long) : CallSignaling {
        val incoming = MutableSharedFlow<CallSignal>(extraBufferCapacity = 16)
        lateinit var other: FakeSignaling
        override val callSignals: SharedFlow<CallSignal> = incoming

        override suspend fun sendCallSignal(to: String, body: Body) {
            other.incoming.emit(CallSignal(me, clock(), body))
        }

        override suspend fun callToken(room: String) = CallToken("wss://example/livekit", "token")

        override suspend fun voiceKey(groupId: String): String? = "voice-key"
    }

    private class Pair(val eva: CallManager, val lera: CallManager, val room: FakeRoom, val evaMedia: FakeMedia, val leraMedia: FakeMedia)

    private fun TestScope.phones(): Pair {
        val clock = { testScheduler.currentTime }
        val room = FakeRoom()
        val evaSignals = FakeSignaling("eva", clock)
        val leraSignals = FakeSignaling("lera", clock)
        evaSignals.other = leraSignals
        leraSignals.other = evaSignals
        val evaMedia = FakeMedia("eva", room, backgroundScope)
        val leraMedia = FakeMedia("lera", room, backgroundScope)
        val eva = CallManager(backgroundScope, evaMedia, evaSignals, { null }, { it }, clock)
        val lera = CallManager(backgroundScope, leraMedia, leraSignals, { null }, { it }, clock)
        runCurrent()
        return Pair(eva, lera, room, evaMedia, leraMedia)
    }

    @Test
    fun answeredCallConnectsBothWithTheSameKey() = runTest {
        val p = phones()
        assertTrue(p.eva.call("lera", video = true))
        runCurrent()
        assertEquals(CallPhase.Calling, p.eva.session.value?.phase)
        val ringing = p.lera.session.value
        assertEquals(CallPhase.Ringing, ringing?.phase)
        assertTrue(ringing!!.incoming && ringing.video)

        p.lera.accept()
        runCurrent()
        assertEquals(CallPhase.Active, p.eva.session.value?.phase)
        assertEquals(CallPhase.Active, p.lera.session.value?.phase)
        assertNotNull(p.evaMedia.joinedWith)
        assertEquals(p.evaMedia.joinedWith, p.leraMedia.joinedWith)

        p.lera.hangUp()
        runCurrent()
        assertEquals(CallOutcome.HangUp, p.eva.session.value?.outcome)
        advanceTimeBy(5_000)
        assertNull(p.eva.session.value)
        assertNull(p.lera.session.value)
    }

    @Test
    fun declineEndsTheCallerToo() = runTest {
        val p = phones()
        p.eva.call("lera", video = false)
        runCurrent()
        p.lera.decline()
        runCurrent()
        assertEquals(CallOutcome.Declined, p.eva.session.value?.outcome)
        assertTrue(p.room.inside.value.isEmpty())
    }

    @Test
    fun secondCallGetsBusy() = runTest {
        val p = phones()
        p.lera.joinVoice("g", "c", "g:c", "созвон")
        runCurrent()
        p.eva.call("lera", video = false)
        runCurrent()
        assertEquals(CallOutcome.Busy, p.eva.session.value?.outcome)
        assertEquals(CallPhase.Active, p.lera.session.value?.phase)
    }

    @Test
    fun unansweredCallStopsRinging() = runTest {
        val p = phones()
        p.eva.call("lera", video = false)
        runCurrent()
        advanceTimeBy(46_000)
        assertEquals(CallOutcome.NoAnswer, p.eva.session.value?.outcome)
        assertEquals(CallOutcome.Missed, p.lera.session.value?.outcome)
    }

    @Test
    fun lateOfferDoesNotRing() = runTest {
        val p = phones()
        advanceTimeBy(120_000)
        val signals = FakeSignaling("anya") { 0L }
        signals.other = FakeSignaling("lera") { 0L }
        // An offer written two minutes ago, delivered only now.
        val lera =
            CallManager(backgroundScope, FakeMedia("x", FakeRoom(), backgroundScope), signals.other, { null }, { it }) {
                testScheduler.currentTime
            }
        runCurrent()
        signals.sendCallSignal("lera", Body.CallOffer("old", video = false, key = "k"))
        runCurrent()
        assertNull(lera.session.value)
        assertNull(p.eva.session.value)
    }
}

private fun <T> CoroutineScope.launchCollect(flow: Flow<T>, action: suspend (T) -> Unit) {
    launch { flow.collect(action) }
}
