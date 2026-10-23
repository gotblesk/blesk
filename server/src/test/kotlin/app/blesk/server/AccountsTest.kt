package app.blesk.server

import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Discovery
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import app.blesk.shared.protocol.Usernames
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
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
    fun `the check names a verdict and the name it compared`() = serverTest { harness ->
        val alice = harness.register()
        val free = randomUsername()
        val expected = mapOf(
            alice.username to UsernameVerdict.TAKEN,
            free to UsernameVerdict.FREE,
            "blesk" to UsernameVerdict.RESERVED,
            "b1esk_team" to UsernameVerdict.RESERVED,
            "egor_bot" to UsernameVerdict.RESERVED,
            "ab" to UsernameVerdict.INVALID,
            "9egor" to UsernameVerdict.INVALID,
        )
        for ((name, verdict) in expected) {
            val check = harness.checkUsername(name)
            assertEquals(name, verdict, check.verdict)
            assertEquals(name, verdict == UsernameVerdict.FREE, check.available)
            assertEquals(name, check.username)
        }
    }

    @Test
    fun `a name in other case is the same name`() = serverTest { harness ->
        val egor = harness.register()
        val check = harness.checkUsername(egor.username.uppercase()).copy(suggestions = emptyList())
        assertEquals(UsernameCheck(available = false, verdict = UsernameVerdict.TAKEN, username = egor.username), check)

        val sameName = harness.registerRaw(egor.username.uppercase(), IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.Conflict, sameName.status)
        assertEquals("username_taken", sameName.body<ErrorBody>().code)
        val capitals = harness.registerRaw(randomUsername().uppercase(), IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.BadRequest, capitals.status)
        assertEquals("invalid_username", capitals.body<ErrorBody>().code)
    }

    @Test
    fun `suggestions are free valid names like the one asked`() = serverTest { harness ->
        val egor = harness.register()
        val suggestions = harness.checkUsername(egor.username).suggestions
        assertTrue(suggestions.toString(), suggestions.size in 1..3)
        for (suggestion in suggestions) {
            assertTrue(suggestion, Usernames.isValid(suggestion))
            assertNotEquals(egor.username, suggestion)
            assertEquals(suggestion, UsernameVerdict.FREE, harness.checkUsername(suggestion).verdict)
        }
    }

    @Test
    fun `a service name gets no suggestions it would refuse`() = serverTest { harness ->
        for (name in listOf("blesk", "support24")) {
            assertEquals(UsernameCheck(available = false, verdict = UsernameVerdict.RESERVED, username = name), harness.checkUsername(name))
        }
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

    @Test
    fun `lookups by username run out, so nobody can walk every name`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        suspend fun lookUp(name: String, token: String) = harness.client.get("${Api.ACCOUNTS}/$name") { bearerAuth(token) }
        val answers = List(25) { lookUp(randomUsername(), alice.token) }
        assertEquals(List(20) { HttpStatusCode.NotFound }, answers.take(20).map { it.status })
        assertTrue(answers.drop(20).all { it.status == HttpStatusCode.TooManyRequests })
        // Ten a minute: a lookup comes back every six seconds.
        assertEquals("6", answers[20].headers[HttpHeaders.RetryAfter])
        // The budget is per person: someone else still finds people.
        assertEquals(HttpStatusCode.OK, lookUp(alice.username, bob.token).status)
    }

    @Test
    fun `a hidden account is found only through its link key`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val hide = harness.client.put(Api.DISCOVERY) {
            bearerAuth(bob.token)
            contentType(ContentType.Application.Json)
            setBody(Discovery(discoverable = false))
        }
        assertEquals(HttpStatusCode.OK, hide.status)

        val byName = harness.client.get("${Api.ACCOUNTS}/${bob.username}") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, byName.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.KEYS}/${bob.username}") { bearerAuth(alice.token) }.status)

        val link = Api.linkKey(bob.identity.publicKey.serialize())
        val byLink = harness.client.get("${Api.ACCOUNTS}/${bob.username}?${Api.LINK}=$link") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.OK, byLink.status)

        val wrong = harness.client.get("${Api.ACCOUNTS}/${bob.username}?${Api.LINK}=nope") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, wrong.status)
    }

    @Test
    fun `a deleted account is gone and its token stops working`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        assertEquals(HttpStatusCode.NoContent, harness.client.delete(Api.ACCOUNT) { bearerAuth(bob.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.ACCOUNTS}/${bob.username}") { bearerAuth(alice.token) }.status)
        assertEquals(HttpStatusCode.Unauthorized, harness.client.get(Api.KEY_STATUS) { bearerAuth(bob.token) }.status)
    }
}
