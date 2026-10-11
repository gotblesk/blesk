package app.blesk.server

import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.ServerInfo
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair

class AccountsTest {
    @Test
    fun `info describes the server`() = serverTest { harness ->
        val response = harness.client.get(Api.INFO)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ServerInfo(), response.body<ServerInfo>())
    }

    @Test
    fun `registers an account and returns its identity key`() = serverTest { harness ->
        val identity = IdentityKeyPair.generate()
        val username = randomUsername()
        val response = harness.registerRaw(username, identity)
        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(AccountInfo(username, identity.publicKey.serialize().b64()), response.body<AccountInfo>())
    }

    @Test
    fun `rejects a taken username`() = serverTest { harness ->
        val alice = harness.register()
        val response = harness.registerRaw(alice.username, IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("username_taken", response.body<ErrorBody>().code)
    }

    @Test
    fun `rejects a registration signed for another name`() = serverTest { harness ->
        val response = harness.registerRaw(randomUsername(), IdentityKeyPair.generate(), signed = "someone_else")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_signature", response.body<ErrorBody>().code)
    }

    @Test
    fun `rejects an invalid username`() = serverTest { harness ->
        val response = harness.registerRaw("No Spaces", IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_username", response.body<ErrorBody>().code)
    }

    @Test
    fun `looks up accounts only when authenticated`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()

        assertEquals(HttpStatusCode.Unauthorized, harness.client.get("${Api.ACCOUNTS}/${bob.username}").status)

        val found = harness.client.get("${Api.ACCOUNTS}/${bob.username}") { bearerAuth(alice.token) }
        assertEquals(AccountInfo(bob.username, bob.identity.publicKey.serialize().b64()), found.body<AccountInfo>())

        val missing = harness.client.get("${Api.ACCOUNTS}/${randomUsername()}") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, missing.status)
    }
}
