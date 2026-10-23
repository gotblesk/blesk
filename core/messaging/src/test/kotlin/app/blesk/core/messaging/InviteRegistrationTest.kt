package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.messaging.FakeServer.Refusal
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Invites
import app.blesk.shared.protocol.ServerInfo
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Registration on a server that lets people in by invite, and the invites every account hands out. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class InviteRegistrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = FakeServer().apply { inviteOnly = true }
    private val databases = mutableListOf<BleskDatabase>()

    /** A fresh phone: its own database, keys and connection. */
    private inner class Phone {
        private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
            .also { databases += it }
        private val store = SignalStore(db.signal())
        private val accounts = AccountManager(store, KeyManager(store))
        val api: BleskApi = server.api()

        fun register(username: String, invite: String?, resume: Boolean = false): RegisterResult =
            runBlocking { accounts.register(api, username, invite = invite, resume = resume) }
    }

    @After
    fun tearDown() = databases.forEach { it.close() }

    @Test
    fun anInviteIsNeededAndWorksOnce() {
        val first = server.bootstrapInvite()
        val second = server.bootstrapInvite()
        assertEquals(RegisterResult.Ok, Phone().register("bob", first))

        assertEquals(RegisterResult.InviteRequired, Phone().register("anya", invite = null))
        assertEquals(RegisterResult.InviteInvalid, Phone().register("anya", "zzzzzzzzzzzz"))
        assertEquals(RegisterResult.InviteInvalid, Phone().register("anya", first))
        // A taken name does not spend the invite: the same code works with another name.
        assertEquals(RegisterResult.NameTaken, Phone().register("bob", second))
        assertEquals(RegisterResult.Ok, Phone().register("anya", second))
        assertEquals(RegisterResult.InviteInvalid, Phone().register("vera", second))
    }

    @Test
    fun aRetryFinishesTheAccountABrokenAttemptMade() {
        val invite = server.bootstrapInvite()
        val anya = Phone()
        server.droppedChallenges = 1
        assertEquals(ServerTrouble.Unreachable, anya.register("anya", invite))

        // The server made the account and spent the invite before the connection dropped.
        assertEquals(RegisterResult.InviteInvalid, anya.register("anya", invite))
        // Another phone cannot finish it: only the key the account was made with signs in.
        assertEquals(RegisterResult.InviteInvalid, Phone().register("anya", invite, resume = true))
        assertEquals(RegisterResult.Ok, anya.register("anya", invite, resume = true))
        assertEquals(Invites.LIMIT, runBlocking { InviteBook(anya.api).list() }?.left)
    }

    @Test
    fun aRetryOfATakenNameIsStillRefused() {
        server.inviteOnly = false
        assertEquals(RegisterResult.Ok, Phone().register("anya", invite = null))
        assertEquals(RegisterResult.NameTaken, Phone().register("anya", invite = null, resume = true))
    }

    @Test
    fun anOpenServerTakesNoInvite() {
        server.inviteOnly = false
        assertEquals(RegisterResult.Ok, Phone().register("anya", invite = null))
    }

    @Test
    fun everyoneHandsOutInvitesUpToTheLimit() {
        val anya = Phone()
        assertEquals(RegisterResult.Ok, anya.register("anya", server.bootstrapInvite()))
        val book = InviteBook(anya.api)
        assertEquals(Invites.LIMIT, runBlocking { book.list() }?.left)

        val made = (1..Invites.LIMIT).map { runBlocking { book.create() } }
        assertTrue(made.all { it is NewInvite.Made })
        assertEquals(NewInvite.Limit, runBlocking { book.create() })
        assertEquals(0, runBlocking { book.list() }?.left)

        val code = (made.first() as NewInvite.Made).invite.code
        assertTrue(Invites.isValid(code))
        assertEquals(RegisterResult.Ok, Phone().register("vera", code))
        val after = runBlocking { book.list() }!!
        assertEquals(1, after.left)
        assertEquals("vera", after.invites.single { it.code == code }.usedBy)
        assertEquals(Invites.LIMIT, after.invites.size)
    }

    @Test
    fun aRefusalForTooManyAttemptsSaysHowLongToWait() {
        server.refuse(Api.ACCOUNTS, Refusal(HttpStatusCode.TooManyRequests, retryAfterSeconds = 120))
        assertEquals(ServerTrouble.TooManyAttempts(120_000), Phone().register("anya", server.bootstrapInvite()))
    }

    @Test
    fun anAppTooOldForTheServerIsToldToUpdate() {
        server.info = ServerInfo(minClientVersion = Api.VERSION + 1)
        assertEquals(ServerTrouble.AppTooOld, Phone().register("anya", server.bootstrapInvite()))
        assertFalse(Api.ACCOUNTS in server.paths)
    }

    @Test
    fun aServerTooOldForTheAppSaysSo() {
        server.info = ServerInfo(version = Api.MIN_SERVER_VERSION - 1)
        assertEquals(ServerTrouble.ServerTooOld, Phone().register("anya", server.bootstrapInvite()))
        assertFalse(Api.ACCOUNTS in server.paths)
    }

    @Test
    fun aServiceNameIsRefusedBeforeAnyRequest() {
        assertEquals(RegisterResult.NameInvalid, Phone().register("support24", server.bootstrapInvite()))
        assertTrue(server.paths.isEmpty())
    }

    @Test
    fun aReservedNameFromTheServerIsAnInvalidName() {
        server.refuse(Api.ACCOUNTS, Refusal(HttpStatusCode.BadRequest, code = "username_reserved"))
        assertEquals(RegisterResult.NameInvalid, Phone().register("anya", server.bootstrapInvite()))
    }

    @Test
    fun aFailedListIsNotAnEmptyOne() {
        val stranger = Phone()
        assertEquals(null, runBlocking { InviteBook(stranger.api).list() })
        assertEquals(NewInvite.Failed, runBlocking { InviteBook(stranger.api).create() })
    }
}
