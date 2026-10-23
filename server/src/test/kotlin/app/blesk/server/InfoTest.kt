package app.blesk.server

import app.blesk.server.spaces.OfficialCommand
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceKind
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InfoTest {
    @Test
    fun `info names the registration mode and the protocol versions`() {
        serverTest { harness ->
            val info = harness.info()
            assertEquals(false, info.invites)
            assertEquals(Api.VERSION, info.version)
            assertEquals(Api.MIN_CLIENT_VERSION, info.minClientVersion)
        }
        serverTest(registration = RegistrationMode.Invite) { harness -> assertEquals(true, harness.info().invites) }
    }

    @Test
    fun `no official channel until the command makes one`() = serverTest(dataSource = TestDatabase.fresh()) { harness ->
        assertNull(harness.info().officialChannel)
        // A channel someone took at the handle before it was kept back is theirs, not the server's.
        val squatter = harness.register()
        val handle = OfficialCommand.HANDLE
        val channel = SpaceChannel(NewPost.MAIN_CHANNEL, handle)
        val squatterId = harness.accountId(squatter)
        assertTrue(harness.services.spaces.create(handle, SpaceKind.Channel, handle, "", squatterId, channel, harness.services.now()))
        assertNull(harness.info().officialChannel)
    }

    @Test
    fun `info names the official channel the command made`() = serverTest(dataSource = TestDatabase.fresh()) { harness ->
        val owner = harness.register()
        val outcome = OfficialCommand(owner.username).run(harness.services.accounts, harness.services.spaces, harness.services.now())
        assertEquals(OfficialCommand.Outcome.Made, outcome)
        assertEquals(OfficialCommand.HANDLE, harness.info().officialChannel)
    }

    private suspend fun Harness.info(): ServerInfo = client.get(Api.INFO).body()
}
