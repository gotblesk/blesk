package app.blesk.server

import app.blesk.server.auth.AccountRepository
import app.blesk.server.auth.Registration
import app.blesk.server.db.Database
import app.blesk.server.invites.InviteCommand
import app.blesk.server.spaces.OfficialCommand
import app.blesk.server.spaces.SpaceRepository
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.time.Instant
import javax.sql.DataSource

class OfficialCommandTest {
    private val now = Instant.now()

    @Test
    fun `the command line takes only official and a well-formed name`() {
        assertEquals(OfficialCommand("egor"), OfficialCommand.parse(listOf("official", "egor")))
        val mistakes = listOf(
            listOf("official"),
            listOf("official", "Egor"),
            listOf("official", "@egor"),
            listOf("official", "a", "b"),
            listOf("officials", "egor"),
        )
        mistakes.forEach { assertNull(it.toString(), OfficialCommand.parse(it)) }
    }

    @Test
    fun `the channel is made once and stays the owner's`(): Unit = runBlocking {
        val server = Repositories(TestDatabase.fresh())
        val owner = server.account()
        val command = OfficialCommand(owner)
        assertEquals(OfficialCommand.Outcome.Made, command.run(server.accounts, server.spaces, now))
        assertEquals(OfficialCommand.Outcome.Already, command.run(server.accounts, server.spaces, now))

        val channel = checkNotNull(server.spaces.find(OfficialCommand.HANDLE))
        assertTrue(channel.official)
        assertEquals(SpaceKind.Channel, channel.kind)
        assertEquals(SpaceRole.Owner, server.spaces.role(channel.id, server.id(owner)))
        assertEquals(OfficialCommand.HANDLE, server.spaces.officialHandle())
    }

    @Test
    fun `an unknown account makes nothing`(): Unit = runBlocking {
        val server = Repositories(TestDatabase.fresh())
        assertEquals(OfficialCommand.Outcome.NoAccount, OfficialCommand("nobody").run(server.accounts, server.spaces, now))
        assertNull(server.spaces.officialHandle())
        assertNull(server.spaces.find(OfficialCommand.HANDLE))
    }

    @Test
    fun `a handle held by another account is not taken over`(): Unit = runBlocking {
        val server = Repositories(TestDatabase.fresh())
        val alice = server.account()
        val bob = server.account()
        server.handOutHandle(bob)

        assertEquals(OfficialCommand.Outcome.HandleTaken, OfficialCommand(alice).run(server.accounts, server.spaces, now))
        assertNull(server.spaces.officialHandle())
        val space = checkNotNull(server.spaces.find(OfficialCommand.HANDLE))
        assertFalse(space.official)
        assertEquals(SpaceRole.Owner, server.spaces.role(space.id, server.id(bob)))
        assertNull(server.spaces.role(space.id, server.id(alice)))
    }

    @Test
    fun `a space its owner made at the handle before becomes official`(): Unit = runBlocking {
        val server = Repositories(TestDatabase.fresh())
        val owner = server.account()
        server.handOutHandle(owner)
        assertEquals(OfficialCommand.Outcome.Made, OfficialCommand(owner).run(server.accounts, server.spaces, now))
        assertEquals(OfficialCommand.HANDLE, server.spaces.officialHandle())
    }

    @Test
    fun `a room its owner made at the handle is not made the channel`(): Unit = runBlocking {
        val server = Repositories(TestDatabase.fresh())
        val owner = server.account()
        server.handOutHandle(owner, SpaceKind.Room)

        val outcome = OfficialCommand(owner).run(server.accounts, server.spaces, now)
        assertEquals(OfficialCommand.Outcome.NotChannel, outcome)
        assertTrue(outcome.status != 0)
        assertNull(server.spaces.officialHandle())
        assertFalse(checkNotNull(server.spaces.find(OfficialCommand.HANDLE)).official)
    }

    @Test
    fun `the server's own process answers with its exit codes`(): Unit = runBlocking {
        val url = TestDatabase.freshUrl()
        val variables = serverProcessVariables(url)

        val nobody = runServerProcess(listOf("official", "nobody"), variables)
        assertEquals(nobody.stderr, 3, nobody.status)
        assertEquals("", nobody.stdout)
        assertTrue(nobody.stderr, nobody.stderr.contains("no account @nobody"))

        // The process above has migrated the database, so an account can go in.
        val owner = Database.pool(DatabaseConfig(url, TestDatabase.USER, "")).use { Repositories(it).account() }
        val made = runServerProcess(listOf("official", owner), variables)
        assertEquals(made.stderr, 0, made.status)
        assertEquals("@${OfficialCommand.HANDLE}", made.stdout.trim())

        val mistake = runServerProcess(listOf("official"), variables)
        assertEquals(2, mistake.status)
        assertEquals("", mistake.stdout)
        assertTrue(mistake.stderr, mistake.stderr.contains(OfficialCommand.USAGE))
        assertTrue(mistake.stderr, mistake.stderr.contains(InviteCommand.USAGE))
    }

    private class Repositories(dataSource: DataSource) {
        private val database = Database(dataSource)
        val accounts = AccountRepository(database)
        val spaces = SpaceRepository(database)

        suspend fun account(): String {
            val username = randomUsername()
            val identity = IdentityKeyPair.generate().publicKey.serialize()
            assertEquals(Registration.Created, accounts.create(username, identity, 1, Instant.now()))
            return username
        }

        suspend fun id(username: String): Long = checkNotNull(accounts.find(username)).id

        /** A space at the official handle, made the way one could be before such handles were kept back. */
        suspend fun handOutHandle(owner: String, kind: SpaceKind = SpaceKind.Channel) {
            val handle = OfficialCommand.HANDLE
            val channel = SpaceChannel(NewPost.MAIN_CHANNEL, handle)
            assertTrue(spaces.create(handle, kind, handle, "", id(owner), channel, Instant.now()))
        }
    }
}
