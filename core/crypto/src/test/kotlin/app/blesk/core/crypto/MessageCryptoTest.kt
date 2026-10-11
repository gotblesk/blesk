package app.blesk.core.crypto

import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.shared.protocol.Api
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.Envelope
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.PreKeyBundleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.signal.libsignal.protocol.IdentityKey
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64

class MessageCryptoTest {

    private class Party(val name: String) {
        val store = SignalStore(FakeStateDao())
        val keys = KeyManager(store).also { it.ensureIdentity() }
        val crypto = MessageCrypto(store) { name }
        val upload = keys.initialUpload()

        fun bundle() = PreKeyBundleDto(
            username = name,
            registrationId = store.localRegistrationId,
            deviceId = Api.DEVICE_ID,
            identityKey = keys.identityPublicKey(),
            signedPreKey = upload.signedPreKey!!,
            preKey = upload.preKeys.first(),
            kyberPreKey = upload.kyberPreKeys.first(),
        )
    }

    private fun content(text: String) = Content("id-$text", 1L, Body.Text(text))

    @Test
    fun `pqxdh session then double ratchet both ways`() {
        val alice = Party("alice")
        val bob = Party("bob")
        alice.crypto.startSession(bob.bundle())

        val first = alice.crypto.encrypt("bob", content("привет"))
        assertEquals(EnvelopeType.PREKEY, first.type)
        val received = bob.crypto.decrypt(Envelope(1, "alice", first.type, first.body, 0))
        assertEquals(Body.Text("привет"), received.body)

        val reply = bob.crypto.encrypt("alice", content("и тебе"))
        assertEquals(EnvelopeType.WHISPER, reply.type)
        assertEquals(Body.Text("и тебе"), alice.crypto.decrypt(Envelope(2, "bob", reply.type, reply.body, 0)).body)
    }

    @Test
    fun `ciphertext hides length within a block and differs every time`() {
        val alice = Party("alice")
        val bob = Party("bob")
        alice.crypto.startSession(bob.bundle())
        val a = alice.crypto.encrypt("bob", content("a"))
        val b = alice.crypto.encrypt("bob", content("a"))
        assertNotEquals(a.body, b.body)
        assertEquals(Base64.getDecoder().decode(a.body).size, Base64.getDecoder().decode(b.body).size)
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val alice = Party("alice")
        val bob = Party("bob")
        alice.crypto.startSession(bob.bundle())
        val sealed = alice.crypto.encrypt("bob", content("секрет"))
        val bytes = Base64.getDecoder().decode(sealed.body)
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        val forged = Base64.getEncoder().encodeToString(bytes)
        assertThrows(Exception::class.java) { bob.crypto.decrypt(Envelope(1, "alice", sealed.type, forged, 0)) }
    }

    @Test
    fun `registration proof verifies with the published identity key`() {
        val alice = Party("alice")
        val request = alice.keys.registration("alice")
        val key = IdentityKey(Base64.getDecoder().decode(request.identityKey))
        assertTrue(key.publicKey.verifySignature(Api.registrationProof("alice"), Base64.getDecoder().decode(request.signature)))
    }

    @Test
    fun `attachments round trip and tampering is detected`() {
        val key = AttachmentCipher.newKey()
        val plain = ByteArray(300_000) { (it % 251).toByte() }
        val encrypted = File.createTempFile("att", ".bin")
        encrypted.outputStream().use { AttachmentCipher.encrypt(key, ByteArrayInputStream(plain), it) }
        val out = ByteArrayOutputStream()
        AttachmentCipher.decrypt(key, encrypted, out)
        assertTrue(plain.contentEquals(out.toByteArray()))

        val bytes = encrypted.readBytes().also { it[100] = (it[100].toInt() xor 1).toByte() }
        encrypted.writeBytes(bytes)
        assertThrows(IOException::class.java) { AttachmentCipher.decrypt(key, encrypted, ByteArrayOutputStream()) }
    }
}
