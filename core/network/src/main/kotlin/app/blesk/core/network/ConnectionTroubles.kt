package app.blesk.core.network

import java.io.IOException
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import java.util.Collections
import java.util.IdentityHashMap
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
 * What a failed request says about the server's certificate, read from every cause and every
 * suppressed failure: the TLS stack wraps what the trust manager threw, and OkHttp throws the failure
 * of the host's first address with those of the others suppressed under it, so a key forged on IPv4
 * can sit beneath an IPv6 route that simply failed. Null when it is not about the certificate: a
 * handshake the network cut off has no certificate cause and is an unreachable server, not a forged one.
 */
fun Throwable.certificateTrouble(): CertificateTrouble? {
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val waiting = ArrayDeque(listOf(this))
    // Bounded, and each failure read once, since causes and suppressed failures can be made to loop.
    while (waiting.isNotEmpty() && seen.size < MAX_FAILURES) {
        val failure = waiting.removeFirst()
        if (!seen.add(failure)) continue
        failure.cause?.let(waiting::addLast)
        waiting.addAll(failure.suppressed)
    }
    return when {
        seen.any { it is PinMismatchException } -> CertificateTrouble.PinMismatch
        seen.any { it.isCertificateFailure() } -> CertificateTrouble.Untrusted
        else -> null
    }
}

private fun Throwable.isCertificateFailure() = this is CertificateException ||
    this is SSLPeerUnverifiedException ||
    this is CertPathValidatorException ||
    this is CertPathBuilderException

private const val MAX_FAILURES = 32
