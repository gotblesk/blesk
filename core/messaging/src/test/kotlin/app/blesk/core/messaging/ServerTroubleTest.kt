package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.phrase.PhraseKeys
import app.blesk.core.crypto.phrase.RecoveryPhrase
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.messaging.FakeServer.Impostor
import app.blesk.core.messaging.FakeServer.Refusal
import app.blesk.core.network.NotBleskException
import app.blesk.core.network.PinMismatchException
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ServerInfo
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException
import java.net.ConnectException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

/** The first look at a server, and the troubles it shares with registration and recovery. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class ServerTroubleTest {
    private val server = FakeServer()
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), BleskDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private fun probe(): ProbeResult = runBlocking { AccountManager.probe(server.api()) }

    private fun recover(): RecoverResult = runBlocking {
        val store = SignalStore(db.signal())
        AccountManager(store, KeyManager(store)).recover(server.api(), PhraseKeys(RecoveryPhrase.newEntropy()))
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun aProbeReadsWhatTheServerIs() {
        val info = ServerInfo(calls = true, invites = true, officialChannel = "blesk")
        server.info = info
        assertEquals(ProbeResult.Ready(info), probe())
        assertEquals(listOf(Api.INFO), server.paths.toList())
    }

    @Test
    fun somethingThatIsNotBleskIsToldApart() {
        server.impostor = Impostor.Html
        assertEquals(ServerTrouble.NotBlesk, probe())
        server.impostor = Impostor.NoRoute
        assertEquals(ServerTrouble.NotBlesk, probe())
    }

    @Test
    fun aBusyServerSaysHowLongToWait() {
        server.refuse(Api.INFO, Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 30))
        assertEquals(ServerTrouble.TooManyAttempts(30_000), probe())
        server.refuse(Api.INFO, Refusal(HttpStatusCode.ServiceUnavailable))
        assertEquals(ServerTrouble.Unreachable, probe())
    }

    @Test
    fun certificateFailuresMapToTheirTroubles() {
        fun handshake(cause: Throwable) = SSLHandshakeException("handshake failed").apply { initCause(cause) }
        val troubles = mapOf<IOException, ServerTrouble>(
            handshake(PinMismatchException()) to ServerTrouble.CertificateMismatch,
            handshake(CertificateExpiredException("expired")) to ServerTrouble.CertificateUntrusted,
            SSLPeerUnverifiedException("Hostname not verified") to ServerTrouble.CertificateUntrusted,
            SSLHandshakeException("Connection reset") to ServerTrouble.Unreachable,
            ConnectException("Connection refused") to ServerTrouble.Unreachable,
            NotBleskException() to ServerTrouble.NotBlesk,
        )
        assertEquals(troubles.values.toList(), troubles.keys.map { it.serverTrouble() })
    }

    @Test
    fun recoveryReportsTheSameTroubles() {
        server.info = ServerInfo(minClientVersion = Api.VERSION + 1)
        assertEquals(ServerTrouble.AppTooOld, recover())
        server.info = null
        server.refuse(Api.RECOVER, Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 5))
        assertEquals(ServerTrouble.TooManyAttempts(5_000), recover())
    }
}
