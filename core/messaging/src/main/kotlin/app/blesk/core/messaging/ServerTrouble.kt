package app.blesk.core.messaging

import app.blesk.core.network.ApiException
import app.blesk.core.network.CertificateTrouble
import app.blesk.core.network.NotBleskException
import app.blesk.core.network.certificateTrouble
import app.blesk.shared.protocol.ServerInfo
import java.io.IOException

/** What a first look at a server found: one this app can use, or a [ServerTrouble]. */
sealed interface ProbeResult {
    data class Ready(val info: ServerInfo) : ProbeResult
}

/** Why the server could not be used at all; the same for the first look, registration and recovery. */
sealed interface ServerTrouble : RegisterResult, RecoverResult, ProbeResult {
    /** Too many attempts from this address; [retryAfterMillis] is how long the server asked to wait, if it said. */
    data class TooManyAttempts(val retryAfterMillis: Long?) : ServerTrouble

    /** The server shows a key other than the one from its QR code: someone may be in between. */
    data object CertificateMismatch : ServerTrouble

    /** The certificate could not be checked: often the phone's date, sometimes a network that intercepts. */
    data object CertificateUntrusted : ServerTrouble

    /** The server serves only newer versions of the app. */
    data object AppTooOld : ServerTrouble

    /** The server is older than this app can work with; its owner has to update it. */
    data object ServerTooOld : ServerTrouble

    /** Something answers at the address, but not a blesk server. */
    data object NotBlesk : ServerTrouble

    data object Unreachable : ServerTrouble
}

internal fun ApiException.serverTrouble(): ServerTrouble =
    if (status == TOO_MANY_REQUESTS) ServerTrouble.TooManyAttempts(retryAfterMillis) else ServerTrouble.Unreachable

internal fun IOException.serverTrouble(): ServerTrouble = if (this is NotBleskException) {
    ServerTrouble.NotBlesk
} else {
    when (certificateTrouble()) {
        CertificateTrouble.PinMismatch -> ServerTrouble.CertificateMismatch
        CertificateTrouble.Untrusted -> ServerTrouble.CertificateUntrusted
        null -> ServerTrouble.Unreachable
    }
}

private const val TOO_MANY_REQUESTS = 429
