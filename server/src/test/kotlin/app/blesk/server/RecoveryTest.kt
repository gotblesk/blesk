package app.blesk.server

import app.blesk.shared.protocol.AccountInfo
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.RecoverRequest
import app.blesk.shared.protocol.RecoveryHash
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKeyPair
import java.security.MessageDigest
import kotlin.random.Random

class RecoveryTest {
    @Test
    fun `the phrase moves the account to a new phone and signs the old one out`() = serverTest { harness ->
        val alice = harness.register()
        val token = Random.nextBytes(32)
        assertEquals(HttpStatusCode.NoContent, harness.setRecovery(alice.token, token).status)

        val phone = IdentityKeyPair.generate()
        val response = harness.recover(token, phone)
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(alice.username, response.body<AccountInfo>().username)

        assertEquals(HttpStatusCode.Unauthorized, harness.client.get(Api.KEY_STATUS) { bearerAuth(alice.token) }.status)
        val oldLogin = harness.tokenRaw(alice.username, harness.challenge(alice.username).nonce, alice.identity)
        assertEquals(HttpStatusCode.Unauthorized, oldLogin.status)
        val newToken = harness.login(alice.username, phone)
        assertEquals(HttpStatusCode.OK, harness.client.get(Api.KEY_STATUS) { bearerAuth(newToken) }.status)
    }

    @Test
    fun `an unknown token opens nothing`() = serverTest { harness ->
        val alice = harness.register()
        harness.setRecovery(alice.token, Random.nextBytes(32))
        assertEquals(HttpStatusCode.Unauthorized, harness.recover(Random.nextBytes(32), IdentityKeyPair.generate()).status)
    }

    @Test
    fun `the new phone must sign with the key it brings`() = serverTest { harness ->
        val alice = harness.register()
        val token = Random.nextBytes(32)
        harness.setRecovery(alice.token, token)
        val response = harness.recover(token, IdentityKeyPair.generate(), signer = IdentityKeyPair.generate())
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals(HttpStatusCode.OK, harness.client.get(Api.KEY_STATUS) { bearerAuth(alice.token) }.status)
    }

    private suspend fun Harness.setRecovery(bearer: String, token: ByteArray): HttpResponse = client.put(Api.RECOVERY) {
        bearerAuth(bearer)
        contentType(ContentType.Application.Json)
        setBody(RecoveryHash(MessageDigest.getInstance("SHA-256").digest(token).b64()))
    }

    private suspend fun Harness.recover(token: ByteArray, phone: IdentityKeyPair, signer: IdentityKeyPair = phone): HttpResponse =
        client.post(Api.RECOVER) {
            contentType(ContentType.Application.Json)
            setBody(
                RecoverRequest(
                    token = token.b64(),
                    identityKey = phone.publicKey.serialize().b64(),
                    registrationId = Random.nextInt(1, 16380),
                    signature = signer.privateKey.calculateSignature(Api.recoveryProof(token.b64())).b64(),
                ),
            )
        }
}
