package app.blesk.core.messaging

import app.blesk.core.data.BotResult
import app.blesk.core.network.BleskApi
import app.blesk.core.network.ServerAddress
import app.blesk.shared.protocol.BotCreated
import app.blesk.shared.protocol.BotInfo
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class BotOwnerTest {
    private val requests = AtomicInteger()

    /** A server that makes whatever bot it is asked for, so a refusal can only come from the phone. */
    private val owner = BotOwner(
        BleskApi(
            ServerAddress("https://blesk.test"),
            MockEngine {
                requests.incrementAndGet()
                val created = BotCreated(BotInfo("weather_bot", "Погода"), "weather_bot:token")
                respond(
                    Json.encodeToString(BotCreated.serializer(), created),
                    HttpStatusCode.Created,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        ),
    )

    @Test
    fun aBotNamedLikeTheServiceIsRefusedBeforeAnyRequest() = runBlocking {
        for (name in listOf("blesk", "support_bot", "@B1esk")) {
            assertEquals(name, BotResult.NameInvalid to null, owner.createBot(name, "x", ""))
        }
        assertEquals(0, requests.get())
    }

    @Test
    fun anOrdinaryBotNameReachesTheServer() = runBlocking {
        assertEquals(BotResult.Ok, owner.createBot("weather", "Погода", "").first)
        assertEquals(1, requests.get())
    }
}
