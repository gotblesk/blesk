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
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Privacy
import app.blesk.core.media.MediaVault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Two real clients (database, libsignal, outbox, receipts) talking through an in-memory server.
 * Everything between them is ciphertext; this checks the plumbing around it end to end.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class TwoClientsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val server = FakeServer()

    private inner class Client(val name: String, displayName: String) {
        val db: BleskDatabase = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
        val store = SignalStore(db.signal())
        val keys = KeyManager(store)
        val accounts = AccountManager(store, keys)
        val api = server.api()
        val repo = ServerChatRepository(
            db = db,
            api = api,
            socket = server.transport(name),
            crypto = MessageCrypto(store) { name },
            keys = keys,
            accounts = accounts,
            media = MediaVault(context),
            me = name,
            scope = scope,
            privacy = { Privacy() },
            profile = { displayName to "" },
            labels = { it?.javaClass?.simpleName.orEmpty() },
            log = EventLog(),
        )

        fun register() = runBlocking { assertEquals(RegisterResult.Ok, accounts.register(api, name)) }
    }

    @After
    fun tearDown() = scope.cancel()

    private fun <T> eventually(block: suspend () -> T?): T = runBlocking {
        withTimeout(TIMEOUT) {
            var value: T? = null
            while (value == null) {
                value = block()
                if (value == null) kotlinx.coroutines.delay(50)
            }
            value
        }
    }

    private fun Client.messages(conversation: String): List<Message> = runBlocking { repo.messages(conversation).first() }

    @Test
    fun conversationEndToEnd() {
        val alice = Client("alice", "Алиса")
        val bob = Client("bob", "Боб")
        alice.register()
        bob.register()
        alice.repo.start()
        bob.repo.start()

        assertEquals("bob", runBlocking { alice.repo.openDirect("bob") })
        alice.repo.send("bob", "привет")

        val received = eventually { bob.messages("alice").firstOrNull() }
        assertEquals("привет", received.text)
        eventually { bob.repo.contact("alice")?.takeIf { it.name == "Алиса" } }
        eventually { alice.messages("bob").firstOrNull()?.takeIf { it.status == SendStatus.Delivered } }

        bob.repo.markRead("alice")
        eventually { alice.messages("bob").firstOrNull()?.takeIf { it.status == SendStatus.Read } }

        bob.repo.toggleReaction("alice", received.id, "🔥")
        eventually { alice.messages("bob").first().reactions.firstOrNull()?.takeIf { it.symbol == "🔥" } }

        val sent = alice.messages("bob").first()
        alice.repo.edit("bob", sent.id, "привет!")
        eventually { bob.messages("alice").firstOrNull()?.takeIf { it.edited && it.text == "привет!" } }

        alice.repo.delete("bob", sent.id)
        eventually { bob.messages("alice").takeIf { it.isEmpty() } }

        val file = File(context.cacheDir, "notes.txt").apply { writeText("заметки") }
        alice.repo.sendAttachment("bob", Attachment.File(file.path, "notes.txt", file.length(), "text/plain"))
        val delivered = eventually {
            (bob.messages("alice").firstOrNull()?.attachment as? Attachment.File)?.takeIf { it.path.isNotEmpty() }
        }
        assertEquals("заметки", File(delivered.path).readText())
        assertTrue(server.transport("bob").acked.isNotEmpty())
    }

    private companion object {
        const val TIMEOUT = 20_000L
    }
}
