package app.blesk.core.network

import java.io.IOException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/** Thrown by [PinTrustManager] only when the server's key is not the one from the QR code. */
class PinMismatchException : CertificateException("Server key does not match the pinned key")

/** Something answers at the address, but not a blesk server: a web page, a Wi-Fi login portal, another service. */
class NotBleskException : IOException("Not a blesk server")

/** Why the server's certificate was not accepted. */
enum class CertificateTrouble {
    /** The address is pinned and the server shows another key: someone may be in between. */
    PinMismatch,

    /** The certificate could not be checked: wrong date on the phone, no trusted issuer, another host name. */
    Untrusted,
}

/**
 * What a failed request says about the server's certificate, read from the whole cause chain, since
 * the TLS stack wraps what the trust manager threw. Null when it is not about the certificate: a
 * handshake the network cut off has no certificate cause and is an unreachable server, not a forged one.
 */
fun Throwable.certificateTrouble(): CertificateTrouble? {
    // Bounded, since causes can be made to loop.
    val chain = generateSequence(this) { it.cause }.take(MAX_CAUSES).toList()
    return when {
        chain.any { it is PinMismatchException } -> CertificateTrouble.PinMismatch
        chain.any { it.isCertificateFailure() } -> CertificateTrouble.Untrusted
        else -> null
    }
}

private fun Throwable.isCertificateFailure() = this is CertificateException ||
    this is SSLPeerUnverifiedException ||
    this is CertPathValidatorException ||
    this is CertPathBuilderException

private const val MAX_CAUSES = 16
