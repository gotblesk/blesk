package app.blesk.server

import app.blesk.server.auth.Registration
import app.blesk.shared.protocol.BotCreated
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.ErrorBody
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.time.Instant

class BotsTest {
    @Test
    fun `an owner creates, lists, renews and deletes a bot`() = serverTest { harness ->
        val owner = harness.register()
        val stranger = harness.register()
        val created = harness.createBotRaw(owner, about = "Повторяет за вами").body<BotCreated>()
        val username = created.bot.username
        assertEquals(BotInfo(username, "Эхо", "Повторяет за вами"), created.bot)
        assertTrue(created.token.startsWith("$username:"))

        val mine = harness.client.get("${Bots.PATH}/mine") { bearerAuth(owner.token) }.body<List<BotInfo>>()
        assertEquals(listOf(created.bot), mine)
        assertEquals(emptyList<BotInfo>(), harness.client.get("${Bots.PATH}/mine") { bearerAuth(stranger.token) }.body<List<BotInfo>>())
        assertEquals(created.bot, harness.client.get(Bots.userPath(username)) { bearerAuth(stranger.token) }.body<BotInfo>())
        assertEquals(HttpStatusCode.OK, harness.botGet(created.token, "getMe").status)

        val strangerRenew = harness.client.post("${Bots.userPath(username)}/token") { bearerAuth(stranger.token) }
        assertEquals(HttpStatusCode.Forbidden, strangerRenew.status)
        val renewed = harness.client.post("${Bots.userPath(username)}/token") { bearerAuth(owner.token) }.body<BotCreated>()
        assertNotEquals(created.token, renewed.token)
        assertEquals(HttpStatusCode.Unauthorized, harness.botGet(created.token, "getMe").status)
        assertEquals(created.bot, harness.botGet(renewed.token, "getMe").body<BotInfo>())

        assertEquals(HttpStatusCode.Forbidden, harness.client.delete(Bots.userPath(username)) { bearerAuth(stranger.token) }.status)
        assertEquals(HttpStatusCode.NoContent, harness.client.delete(Bots.userPath(username)) { bearerAuth(owner.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get(Bots.userPath(username)) { bearerAuth(owner.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, harness.botGet(renewed.token, "getMe").status)
    }

    @Test
    fun `bad usernames, names and too many bots are rejected`() = serverTest { harness ->
        val owner = harness.register()
        assertEquals("invalid_username", harness.createBotRaw(owner, username = "plain_name").body<ErrorBody>().code)
        assertEquals("invalid_username", harness.createBotRaw(owner, username = "Big_bot").body<ErrorBody>().code)
        assertEquals("invalid_name", harness.createBotRaw(owner, name = "  ").body<ErrorBody>().code)
        assertEquals("invalid_name", harness.createBotRaw(owner, name = "x".repeat(65)).body<ErrorBody>().code)
        assertEquals("invalid_name", harness.createBotRaw(owner, about = "x".repeat(281)).body<ErrorBody>().code)

        repeat(Bots.MAX_BOTS_PER_OWNER) { harness.createBot(owner) }
        val extra = harness.createBotRaw(owner)
        assertEquals(HttpStatusCode.Conflict, extra.status)
        assertEquals("too_many_bots", extra.body<ErrorBody>().code)
    }

    @Test
    fun `bots and people never share a username`() = serverTest { harness ->
        val owner = harness.register()
        val bot = harness.createBot(owner).bot.username

        val sameBot = harness.createBotRaw(harness.register(), username = bot)
        assertEquals(HttpStatusCode.Conflict, sameBot.status)
        assertEquals("username_taken", sameBot.body<ErrorBody>().code)

        val personAsBot = harness.registerRaw(bot, IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.BadRequest, personAsBot.status)
        assertEquals("username_reserved", personAsBot.body<ErrorBody>().code)
        val personWithSuffix = harness.registerRaw(randomBotUsername(), IdentityKeyPair.generate())
        assertEquals("username_reserved", personWithSuffix.body<ErrorBody>().code)

        // An account registered before such names were reserved keeps its name.
        val legacy = randomBotUsername()
        val created = harness.services.accounts.create(legacy, IdentityKeyPair.generate().publicKey.serialize(), 1, Instant.now())
        assertEquals(Registration.Created, created)
        val botOverPerson = harness.createBotRaw(owner, username = legacy)
        assertEquals(HttpStatusCode.Conflict, botOverPerson.status)
        assertEquals("username_taken", botOverPerson.body<ErrorBody>().code)
    }

    @Test
    fun `the bot API refuses missing and wrong tokens`() = serverTest { harness ->
        val created = harness.createBot(harness.register())
        assertEquals(HttpStatusCode.Unauthorized, harness.client.get("${Bots.API_PATH}/getMe").status)
        assertEquals(HttpStatusCode.Unauthorized, harness.botGet("${created.bot.username}:forged", "getMe").status)
        assertEquals(HttpStatusCode.Unauthorized, harness.botGet(created.token + "x", "getMe").status)
        val personToken = harness.register().token
        val asPerson = harness.client.get("${Bots.API_PATH}/getMe") { header(HttpHeaders.Authorization, "Bearer $personToken") }
        assertEquals(HttpStatusCode.Unauthorized, asPerson.status)
        assertEquals("unauthorized", asPerson.body<ErrorBody>().code)
    }

    @Test
    fun `an unknown bot is not found`() = serverTest { harness ->
        val person = harness.register()
        assertEquals(HttpStatusCode.NotFound, harness.client.get(Bots.userPath(randomBotUsername())) { bearerAuth(person.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.writeToBot(person, randomBotUsername(), text = "привет").status)
    }
}
