package app.blesk.server

import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.KeyStatus
import app.blesk.shared.protocol.PreKeyBundleDto
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.signal.libsignal.protocol.ecc.ECKeyPair

class KeysTest {
    @Test
    fun `bundles hand out one-time keys once and then fall back to the last resort key`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val upload = harness.keys(bob, preKeys = 2, kyberPreKeys = 2)
        assertEquals(KeyStatus(2, 2), harness.uploadKeys(bob, upload).body<KeyStatus>())

        val bundles = (1..3).map { harness.client.get("${Api.KEYS}/${bob.username}") { bearerAuth(alice.token) }.body<PreKeyBundleDto>() }

        assertEquals(listOf(1, 2), bundles.take(2).map { it.preKey?.id })
        assertEquals(listOf(1, 2), bundles.take(2).map { it.kyberPreKey.id })
        assertNull(bundles[2].preKey)
        assertEquals(Harness.LAST_RESORT_ID, bundles[2].kyberPreKey.id)
        assertEquals(upload.kyberLastResort, bundles[2].kyberPreKey)
        assertEquals(upload.signedPreKey, bundles[2].signedPreKey)
        assertEquals(bob.identity.publicKey.serialize().b64(), bundles[2].identityKey)
        assertEquals(Api.DEVICE_ID, bundles[2].deviceId)
        assertEquals(KeyStatus(0, 0), harness.client.get(Api.KEY_STATUS) { bearerAuth(bob.token) }.body<KeyStatus>())
    }

    @Test
    fun `one-time keys add up while signed keys are replaced`() = serverTest { harness ->
        val bob = harness.register()
        harness.uploadKeys(bob, harness.keys(bob, preKeys = 3, kyberPreKeys = 1))
        val second = harness.uploadKeys(bob, harness.keys(bob, preKeys = 2, kyberPreKeys = 2, firstId = 100))
        assertEquals(KeyStatus(5, 3), second.body<KeyStatus>())
    }

    @Test
    fun `uploads beyond the cap are refused`() = serverTest { harness ->
        val bob = harness.register()
        val pre = harness.keys(bob, preKeys = 0, kyberPreKeys = 0)
        val tooMany = pre.copy(preKeys = (1..201).map { harness.keys(bob, 1, 0, firstId = it).preKeys.single() })
        val response = harness.uploadKeys(bob, tooMany)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("too_many_keys", response.body<ErrorBody>().code)
    }

    @Test
    fun `a signed prekey with a bad signature is rejected`() = serverTest { harness ->
        val bob = harness.register()
        val upload = harness.keys(bob, preKeys = 1, kyberPreKeys = 1)
        val otherKey = ECKeyPair.generate().publicKey.serialize().b64()
        val forged = upload.copy(signedPreKey = upload.signedPreKey!!.copy(publicKey = otherKey))
        val response = harness.uploadKeys(bob, forged)
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("invalid_signature", response.body<ErrorBody>().code)
        assertEquals(KeyStatus(0, 0), harness.client.get(Api.KEY_STATUS) { bearerAuth(bob.token) }.body<KeyStatus>())
    }

    @Test
    fun `a kyber key signed by someone else is rejected`() = serverTest { harness ->
        val bob = harness.register()
        val mallory = harness.register()
        val upload = harness.keys(bob, preKeys = 0, kyberPreKeys = 0)
        val response = harness.uploadKeys(bob, upload.copy(kyberPreKeys = harness.keys(mallory, 0, 1).kyberPreKeys))
        assertEquals("invalid_signature", response.body<ErrorBody>().code)
    }

    @Test
    fun `no bundle without a signed prekey or for unknown users`() = serverTest { harness ->
        val alice = harness.register()
        val bob = harness.register()
        val noKeys = harness.client.get("${Api.KEYS}/${bob.username}") { bearerAuth(alice.token) }
        assertEquals(HttpStatusCode.NotFound, noKeys.status)
        assertEquals("no_keys", noKeys.body<ErrorBody>().code)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Api.KEYS}/${randomUsername()}") { bearerAuth(alice.token) }.status)
    }
}
