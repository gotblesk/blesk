package app.blesk.core.messaging

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.core.network.ServerAddress
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

/**
 * A sync stores posts the server listed before the live edits and removals that reach the phone while it is under way.
 * Here the server's answer is held after it was made, and a live change comes in the meantime.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class SpaceEngineTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, BleskDatabase::class.java).allowMainThreadQueries().build()
    private val json = Json { encodeDefaults = true }
    private val club = SpaceInfo(HANDLE, SpaceKind.Channel, "Клуб", about = "", members = 2, channels = emptyList())
    private val post = Post(1, HANDLE, "main", "bob", "анонс", createdAtMillis = 1)

    /** The posts the server lists; what it has when a request comes is what the answer holds. */
    @Volatile
    private var onServer = listOf(post)
    private val answer = CompletableDeferred<Unit>()
    private val listings = AtomicInteger()
    private val api = BleskApi(
        ServerAddress("https://blesk.test"),
        MockEngine { request ->
            val body = when (request.url.encodedPath) {
                "/v1/spaces/mine" -> json.encodeToString(ListSerializer(SpaceInfo.serializer()), listOf(club))
                "/v1/spaces/$HANDLE/posts" -> {
                    val listed = json.encodeToString(ListSerializer(Post.serializer()), onServer)
                    listings.incrementAndGet()
                    answer.await()
                    listed
                }
                else -> return@MockEngine respond("", HttpStatusCode.NotFound)
            }
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )
    private val engine = SpaceEngine(db, api, MediaVault(context), ME) { "" }
    private val id = SpaceEngine.messageId(HANDLE, post.id)

    @After
    fun tearDown() = db.close()

    @Test
    fun anEditThatCameDuringASyncStays() = runBlocking {
        engine.store(post)
        val sync = launch(Dispatchers.IO) { engine.sync() }
        untilTheServerHasListed()
        engine.store(post.copy(text = "анонс!", editedAtMillis = 2))
        answer.complete(Unit)
        sync.join()

        val row = db.messages().get(id)!!
        assertEquals("анонс!", row.text)
        assertTrue(row.edited)
    }

    @Test
    fun aPostRemovedDuringASyncStaysRemoved() = runBlocking {
        engine.store(post)
        val sync = launch(Dispatchers.IO) { engine.sync() }
        untilTheServerHasListed()
        engine.removed(HANDLE, post.id)
        answer.complete(Unit)
        sync.join()

        assertNull(db.messages().get(id))
    }

    @Test
    fun aSyncStillBringsWhatChangedBeforeIt() = runBlocking {
        engine.store(post)
        onServer = listOf(post.copy(text = "анонс!", editedAtMillis = 2), post.copy(id = 2, text = "второй"))
        answer.complete(Unit)
        engine.sync()

        assertEquals("анонс!", db.messages().get(id)?.text)
        assertEquals("второй", db.messages().get(SpaceEngine.messageId(HANDLE, 2))?.text)
    }

    private suspend fun untilTheServerHasListed() = withTimeout(TIMEOUT) {
        while (listings.get() == 0) delay(POLL)
    }

    private companion object {
        const val ME = "alice"
        const val HANDLE = "club"
        const val TIMEOUT = 60_000L
        const val POLL = 20L
    }
}
