package app.blesk.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.SSLHandshakeException

/** Fixtures hold public certificates only, valid 2000–2199 and 2000–2001; their pins were computed by OpenSSL. */
class PinTrustManagerTest {
    private val server = certificate("server-cert.pem")
    private val serverPin = "sha256/RQQir5udVeDCC8agsXc54FpLypKhdH5Y50oXlXu//ME="
    private val expired = certificate("expired-cert.pem")
    private val expiredPin = "sha256/+wGAXdLjSVCSxWLqRBARt+0jZbVFxIa8bUM7HpGCkg4="

    private fun certificate(name: String): X509Certificate = javaClass.classLoader!!.getResourceAsStream(name).use {
        CertificateFactory.getInstance("X.509").generateCertificate(it) as X509Certificate
    }

    private fun refusal(pin: String, vararg chain: X509Certificate): CertificateException =
        assertThrows(CertificateException::class.java) { PinTrustManager(pin).checkServerTrusted(arrayOf(*chain), "ECDHE_ECDSA") }

    private fun CertificateException.trouble() =
        SSLHandshakeException("handshake failed").apply { initCause(this@trouble) }.certificateTrouble()

    @Test
    fun `the pinned key passes and any other is a mismatch`() {
        PinTrustManager(serverPin).checkServerTrusted(arrayOf(server), "ECDHE_ECDSA")
        val refused = refusal("sha256/" + "A".repeat(43) + "=", server)
        assertEquals(PinMismatchException::class.java, refused.javaClass)
        assertEquals(CertificateTrouble.PinMismatch, refused.trouble())
    }

    @Test
    fun `an expired certificate with the pinned key is untrusted, not a mismatch`() {
        val refused = refusal(expiredPin, expired)
        assertEquals(CertificateExpiredException::class.java, refused.javaClass)
        assertEquals(CertificateTrouble.Untrusted, refused.trouble())
    }

    @Test
    fun `an empty chain is refused but not as a mismatch`() {
        assertEquals(CertificateTrouble.Untrusted, refusal(serverPin).trouble())
    }
}
