package app.blesk.server

import app.blesk.server.auth.AccountRepository
import app.blesk.server.auth.Registration
import app.blesk.server.db.Database
import app.blesk.server.db.query
import app.blesk.server.invites.InviteCommand
import app.blesk.server.invites.InviteRepository
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList
import app.blesk.shared.protocol.Invites
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.sql.DataSource
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class InvitesTest {
    private val clock = TestClock()

    @Test
    fun `an invite-only server refuses a registration without a working invite`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val used = harness.bootstrapInvite().also { harness.register(invite = it) }
        val expired = harness.createInvite(alice).code
        clock.advance(Invites.TTL_DAYS.days)

        val refusals = mapOf(
            null to "invite_required",
            " " to "invite_required",
            "not a code" to "invite_invalid",
            "a".repeat(Invites.CODE_LENGTH) to "invite_invalid",
            used to "invite_invalid",
            expired to "invite_invalid",
        )
        refusals.forEach { (invite, code) ->
            val response = harness.registerRaw(randomUsername(), IdentityKeyPair.generate(), invite = invite)
            assertEquals("$invite", HttpStatusCode.Forbidden, response.status)
            assertEquals("$invite", code, response.body<ErrorBody>().code)
        }
    }

    @Test
    fun `the name, key and signature are checked before the invite`() = inviteServer { harness ->
        val badName = harness.registerRaw("No Spaces", IdentityKeyPair.generate())
        assertEquals("invalid_username", badName.body<ErrorBody>().code)
        val forged = harness.registerRaw(randomUsername(), IdentityKeyPair.generate(), signed = "someone_else")
        assertEquals("invalid_signature", forged.body<ErrorBody>().code)
    }

    @Test
    fun `an invite lets one person in and then shows who came by it`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val invite = harness.createInvite(alice)
        assertEquals(clock.millis(), invite.createdAtMillis)
        assertEquals(clock.millis() + Invites.TTL_DAYS.days.inWholeMilliseconds, invite.expiresAtMillis)
        assertEquals(InviteList(listOf(invite), left = Invites.LIMIT - 1), harness.invites(alice))

        clock.advance(1.minutes)
        // The link from the site works as well as the bare code.
        val bob = harness.register(invite = Invites.link(invite.code))
        val again = harness.registerRaw(randomUsername(), IdentityKeyPair.generate(), invite = invite.code)
        assertEquals(HttpStatusCode.Forbidden, again.status)
        assertEquals("invite_invalid", again.body<ErrorBody>().code)

        val used = invite.copy(usedBy = bob.username, usedAtMillis = clock.millis())
        assertEquals(InviteList(listOf(used), left = Invites.LIMIT), harness.invites(alice))
    }

    @Test
    fun `a taken name leaves the invite usable`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val invite = harness.createInvite(alice).code
        val taken = harness.registerRaw(alice.username, IdentityKeyPair.generate(), invite = invite)
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("username_taken", taken.body<ErrorBody>().code)
        harness.register(invite = invite)
    }

    @Test
    fun `five invites wait at most, and used or expired ones free their place`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val made = List(Invites.LIMIT) {
            clock.advance(1.minutes)
            harness.createInvite(alice)
        }
        val extra = harness.createInviteRaw(alice)
        assertEquals(HttpStatusCode.Conflict, extra.status)
        assertEquals("invite_limit", extra.body<ErrorBody>().code)
        assertEquals(InviteList(made.reversed(), left = 0), harness.invites(alice))

        harness.register(invite = made.first().code)
        assertEquals(1, harness.invites(alice).left)
        clock.advance(Invites.TTL_DAYS.days)
        val left = harness.invites(alice)
        assertEquals(listOf(made.first().code), left.invites.map(InviteInfo::code))
        assertEquals(Invites.LIMIT, left.left)
        assertEquals(HttpStatusCode.Created, harness.createInviteRaw(alice).status)
    }

    @Test
    fun `who came by an invite is forgotten thirty days later`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val invite = harness.createInvite(alice).code
        harness.register(invite = invite)
        clock.advance(Invites.KEEP_DAYS.days - 1.milliseconds)
        assertEquals(listOf(invite), harness.invites(alice).invites.map(InviteInfo::code))

        clock.advance(1.milliseconds)
        // Alice's token has run out by now too.
        val renewed = TestUser(alice.username, alice.identity, harness.login(alice.username, alice.identity))
        assertEquals(InviteList(emptyList(), left = Invites.LIMIT), harness.invites(renewed))
    }

    @Test
    fun `a person sees only their own invites`() = inviteServer { harness ->
        val alice = harness.register(invite = harness.bootstrapInvite())
        val mallory = harness.register(invite = harness.bootstrapInvite())
        harness.createInvite(alice)
        assertEquals(InviteList(emptyList(), left = Invites.LIMIT), harness.invites(mallory))
    }

    @Test
    fun `an open server lets anyone in and leaves invites alone`() = serverTest(clock = clock) { harness ->
        val alice = harness.register()
        val invite = harness.createInvite(alice)
        harness.register(invite = invite.code)
        assertEquals(listOf(invite), harness.invites(alice).invites)
    }

    @Test
    fun `invites share the registration budget and say when to ask again`() =
        serverTest(rateLimit = RateLimitConfig(capacity = 4, refillPerMinute = 3)) { harness ->
            // Registering and signing in take three of the four.
            val alice = harness.register()
            harness.createInvite(alice)
            val limited = harness.createInviteRaw(alice)
            assertEquals(HttpStatusCode.TooManyRequests, limited.status)
            assertEquals("rate_limited", limited.body<ErrorBody>().code)
            assertEquals("20", limited.headers[HttpHeaders.RetryAfter])
        }

    @Test
    fun `maintenance deletes expired invites, and used ones once forgotten`() {
        val dataSource = TestDatabase.fresh()
        inviteServer(dataSource) { harness ->
            val first = harness.bootstrapInvite()
            val alice = harness.register(invite = first)
            val used = harness.createInvite(alice).code
            harness.createInvite(alice)
            harness.register(invite = used)
            val waiting = harness.bootstrapInvite()

            clock.advance(Invites.TTL_DAYS.days)
            runMaintenance(harness.services)
            assertEquals(setOf(first, used, waiting), inviteCodes(dataSource))
            clock.advance(Invites.KEEP_DAYS.days)
            runMaintenance(harness.services)
            assertEquals(setOf(waiting), inviteCodes(dataSource))
        }
    }

    @Test
    fun `the invites command makes ten bootstrap invites once and add makes more`(): Unit = runBlocking {
        val database = Database(TestDatabase.fresh())
        val invites = InviteRepository(database)
        val now = clock.instant()
        val links = InviteCommand.Print.run(invites, now)
        assertEquals(InviteCommand.FIRST_BATCH, links.toSet().size)
        assertTrue(links.all { it.startsWith("https://blesk.su/i/") && Invites.parse(it) != null })
        assertEquals(links, InviteCommand.Print.run(invites, now))

        val code = checkNotNull(Invites.parse(links.first()))
        val key = IdentityKeyPair.generate().publicKey.serialize()
        assertEquals(Registration.Created, AccountRepository(database).create("owner", key, 1, now, invite = code))
        assertEquals(links.drop(1), InviteCommand.Print.run(invites, now))

        val more = InviteCommand.Add(3).run(invites, now)
        assertEquals(InviteCommand.FIRST_BATCH + 2, more.size)
        assertTrue(more.containsAll(links.drop(1)))
    }

    @Test
    fun `the invites command makes no first batch once bootstrap invites were ever made`(): Unit = runBlocking {
        val invites = InviteRepository(Database(TestDatabase.fresh()))
        val added = InviteCommand.Add(2).run(invites, clock.instant())
        assertEquals(2, added.size)
        assertEquals(added, InviteCommand.Print.run(invites, clock.instant()))
    }

    @Test
    fun `the server's own process prints nothing but the links, and exits with 2 on a mistake`() {
        val variables = mapOf(
            "BLESK_DB_URL" to TestDatabase.freshUrl(),
            "BLESK_DB_USER" to TestDatabase.USER,
            "BLESK_STORAGE" to "fs",
        )
        val run = runServer(listOf("invites"), variables)
        assertEquals(run.stderr, 0, run.status)
        val links = run.stdout.lines().dropLastWhile(String::isEmpty)
        assertEquals(InviteCommand.FIRST_BATCH, links.size)
        assertTrue(run.stdout, links.all { it.startsWith("https://blesk.su/i/") && Invites.parse(it) != null })

        val mistake = runServer(listOf("invites", "add", "0"), variables)
        assertEquals(2, mistake.status)
        assertEquals("", mistake.stdout)
        assertTrue(mistake.stderr, mistake.stderr.contains(InviteCommand.USAGE))
    }

    @Test
    fun `the command line takes only the forms it knows`() {
        assertEquals(InviteCommand.Print, InviteCommand.parse(listOf("invites")))
        assertEquals(InviteCommand.Add(1), InviteCommand.parse(listOf("invites", "add", "1")))
        assertEquals(InviteCommand.Add(50), InviteCommand.parse(listOf("invites", "add", "50")))
        val mistakes = listOf(
            listOf("invite"),
            listOf("invites", "list"),
            listOf("invites", "add"),
            listOf("invites", "add", "0"),
            listOf("invites", "add", "51"),
            listOf("invites", "add", "ten"),
            listOf("invites", "add", "1", "2"),
        )
        mistakes.forEach { assertNull(it.toString(), InviteCommand.parse(it)) }
    }

    @Test
    fun `registration is by invite unless set open`() {
        val base = mapOf("BLESK_STORAGE" to "fs")
        assertEquals(RegistrationMode.Invite, Config.fromEnvironment(base).registration)
        assertEquals(RegistrationMode.Open, Config.fromEnvironment(base + ("BLESK_REGISTRATION" to "open")).registration)
        assertThrows(IllegalStateException::class.java) { Config.fromEnvironment(base + ("BLESK_REGISTRATION" to "closed")) }
    }

    private class Run(val status: Int, val stdout: String, val stderr: String)

    /** Starts the server's main in a process of its own, as `blesk-server` runs on the host. */
    private fun runServer(args: List<String>, variables: Map<String, String>): Run {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val command = listOf(java, "-cp", System.getProperty("java.class.path"), "app.blesk.server.MainKt") + args
        val process = ProcessBuilder(command)
            .apply {
                environment().keys.removeIf { it.startsWith("BLESK_") }
                environment().putAll(variables)
            }
            .start()
        val stderr = CompletableFuture.supplyAsync { process.errorStream.readBytes().decodeToString() }
        val stdout = process.inputStream.readBytes().decodeToString()
        if (!process.waitFor(1, TimeUnit.MINUTES)) {
            process.destroyForcibly()
            throw AssertionError("blesk-server $args did not exit")
        }
        return Run(process.exitValue(), stdout, stderr.get())
    }

    private fun inviteServer(
        dataSource: DataSource = TestDatabase.dataSource,
        block: suspend ApplicationTestBuilder.(Harness) -> Unit,
    ) = serverTest(registration = RegistrationMode.Invite, clock = clock, dataSource = dataSource, block = block)

    private suspend fun Harness.bootstrapInvite(): String = services.invites.createBootstrap(1, services.now()).single()

    private suspend fun Harness.createInviteRaw(user: TestUser): HttpResponse = client.post(Api.INVITES) { bearerAuth(user.token) }

    private suspend fun Harness.createInvite(user: TestUser): InviteInfo {
        val response = createInviteRaw(user)
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    private suspend fun Harness.invites(user: TestUser): InviteList = client.get(Api.INVITES) { bearerAuth(user.token) }.body()

    private suspend fun inviteCodes(dataSource: DataSource): Set<String> =
        Database(dataSource).transaction { connection -> connection.query("SELECT code FROM invites") { it.getString("code") } }.toSet()
}
