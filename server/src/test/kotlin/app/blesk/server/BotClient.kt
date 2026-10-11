package app.blesk.server

import app.blesk.shared.protocol.BotCreated
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CallbackPress
import app.blesk.shared.protocol.CreateBot
import app.blesk.shared.protocol.Update
import app.blesk.shared.protocol.UserToBot
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import java.util.UUID

fun randomBotUsername(): String = "b" + (1..10).map { "abcdefghijklmnopqrstuvwxyz0123456789".random() }.joinToString("") + Bots.SUFFIX

suspend fun Harness.createBotRaw(
    owner: TestUser,
    username: String = randomBotUsername(),
    name: String = "Эхо",
    about: String = "",
): HttpResponse =
    client.post(Bots.PATH) {
        bearerAuth(owner.token)
        contentType(ContentType.Application.Json)
        setBody(CreateBot(username, name, about))
    }

suspend fun Harness.createBot(owner: TestUser, username: String = randomBotUsername()): BotCreated {
    val response = createBotRaw(owner, username)
    assertEquals(HttpStatusCode.Created, response.status)
    return response.body()
}

suspend fun Harness.writeToBot(
    person: TestUser,
    bot: String,
    text: String? = null,
    callback: CallbackPress? = null,
    id: String = UUID.randomUUID().toString(),
): HttpResponse =
    client.post("${Bots.userPath(bot)}/updates") {
        bearerAuth(person.token)
        contentType(ContentType.Application.Json)
        setBody(UserToBot(id, text, callback))
    }

/** A Bot API call; the token goes in the raw header because it contains a colon. */
suspend fun Harness.botGet(token: String, method: String): HttpResponse =
    client.get("${Bots.API_PATH}/$method") { header(HttpHeaders.Authorization, "Bearer $token") }

suspend inline fun <reified T> Harness.botPost(token: String, method: String, body: T): HttpResponse =
    client.post("${Bots.API_PATH}/$method") {
        header(HttpHeaders.Authorization, "Bearer $token")
        contentType(ContentType.Application.Json)
        setBody(body)
    }

suspend fun Harness.botPostEmpty(token: String, method: String): HttpResponse =
    client.post("${Bots.API_PATH}/$method") { header(HttpHeaders.Authorization, "Bearer $token") }

suspend fun Harness.getUpdates(token: String, offset: Long = 0, timeout: Int = 0): List<Update> {
    val response = botGet(token, "getUpdates?offset=$offset&timeout=$timeout")
    assertEquals(HttpStatusCode.OK, response.status)
    return response.body()
}
