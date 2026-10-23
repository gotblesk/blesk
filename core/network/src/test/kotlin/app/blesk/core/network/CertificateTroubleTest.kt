package app.blesk.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class CertificateTroubleTest {
    private fun handshake(cause: Throwable?) = SSLHandshakeException("handshake failed").apply { initCause(cause) }

    @Test
    fun `a key other than the pinned one is a mismatch however deep it is wrapped`() {
        assertEquals(CertificateTrouble.PinMismatch, handshake(PinMismatchException()).certificateTrouble())
        assertEquals(CertificateTrouble.PinMismatch, IllegalStateException(handshake(PinMismatchException())).certificateTrouble())
    }

    @Test
    fun `a certificate that cannot be checked is untrusted, not a mismatch`() {
        assertEquals(CertificateTrouble.Untrusted, handshake(CertificateExpiredException("expired")).certificateTrouble())
        val noAnchor = CertificateException(CertPathValidatorException("Trust anchor for certification path not found"))
        assertEquals(CertificateTrouble.Untrusted, handshake(noAnchor).certificateTrouble())
        assertEquals(CertificateTrouble.Untrusted, handshake(CertPathValidatorException("no anchor")).certificateTrouble())
        assertEquals(CertificateTrouble.Untrusted, SSLPeerUnverifiedException("Hostname 10.0.0.1 not verified").certificateTrouble())
    }

    @Test
    fun `a handshake cut by the network is not about the certificate`() {
        assertNull(SSLHandshakeException("Connection reset").certificateTrouble())
        assertNull(ConnectException("Connection refused").certificateTrouble())
        assertNull(UnknownHostException("api.blesk.su").certificateTrouble())
        assertNull(SocketTimeoutException("timeout").certificateTrouble())
    }

    @Test
    fun `a certificate failure on another address of the server is still found`() {
        // OkHttp tries every address of the host and throws the first failure, with the later ones suppressed under it.
        val pinMismatchOnIpv4 = ConnectException("IPv6 unreachable").apply { addSuppressed(handshake(PinMismatchException())) }
        assertEquals(CertificateTrouble.PinMismatch, pinMismatchOnIpv4.certificateTrouble())
        // Coroutines may rethrow a copy of the failure with the original as its cause.
        assertEquals(CertificateTrouble.PinMismatch, SocketException("copy").apply { initCause(pinMismatchOnIpv4) }.certificateTrouble())
        val expiredOnIpv4 = ConnectException("IPv6 unreachable").apply { addSuppressed(handshake(CertificateExpiredException("expired"))) }
        assertEquals(CertificateTrouble.Untrusted, expiredOnIpv4.certificateTrouble())
        val bothUnreachable = ConnectException("IPv6 unreachable").apply { addSuppressed(SocketTimeoutException("IPv4 timeout")) }
        assertNull(bothUnreachable.certificateTrouble())
    }

    @Test(timeout = 10_000)
    fun `failures suppressed under each other end the walk`() {
        val first = SSLHandshakeException("first")
        val second = SSLHandshakeException("second")
        first.addSuppressed(second)
        second.addSuppressed(first)
        assertNull(first.certificateTrouble())
    }

    @Test(timeout = 10_000)
    fun `causes that loop end the walk`() {
        val first = SSLHandshakeException("first")
        val second = SSLHandshakeException("second")
        first.initCause(second)
        second.initCause(first)
        assertNull(first.certificateTrouble())
    }
}
