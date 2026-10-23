package app.blesk.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import app.blesk.core.design.component.formatWait

/**
 * Why getting onto a server did not work, as the entry screens say it. [Unreachable], [NotBlesk]
 * and [CertificateUntrusted] know whether the server is blesk's own: for another one its address
 * or its QR code is the first thing to check.
 */
@Immutable
sealed interface AccessProblem {
    /** What was typed is not a server address. */
    data object BadAddress : AccessProblem

    data class Unreachable(val otherServer: Boolean) : AccessProblem

    /** Something answers at the address, but not a blesk server. */
    data class NotBlesk(val otherServer: Boolean) : AccessProblem

    /** The server shows a key other than the one from its QR code. */
    data object CertificateMismatch : AccessProblem

    /** The certificate could not be checked: often the phone's date, sometimes a network that intercepts. */
    data class CertificateUntrusted(val otherServer: Boolean) : AccessProblem

    data object AppTooOld : AccessProblem

    data object ServerTooOld : AccessProblem

    /** The server asked to wait [waitMillis] before the next attempt. */
    data class TooManyAttempts(val waitMillis: Long) : AccessProblem

    data object InviteRequired : AccessProblem

    data object InviteInvalid : AccessProblem

    data object NameTaken : AccessProblem

    data object NameInvalid : AccessProblem
}

@Composable
fun AccessProblem.text(): String = when (this) {
    AccessProblem.BadAddress -> stringResource(R.string.onboarding_error_address)
    is AccessProblem.Unreachable ->
        stringResource(if (otherServer) R.string.onboarding_error_unreachable_other else R.string.onboarding_error_unreachable)
    is AccessProblem.NotBlesk ->
        stringResource(if (otherServer) R.string.onboarding_error_not_blesk_other else R.string.onboarding_error_not_blesk)
    AccessProblem.CertificateMismatch -> stringResource(R.string.onboarding_error_certificate_mismatch)
    is AccessProblem.CertificateUntrusted ->
        stringResource(if (otherServer) R.string.onboarding_error_certificate_other else R.string.onboarding_error_certificate)
    AccessProblem.AppTooOld -> stringResource(R.string.onboarding_error_app_old)
    AccessProblem.ServerTooOld -> stringResource(R.string.onboarding_error_server_old)
    is AccessProblem.TooManyAttempts -> stringResource(R.string.onboarding_error_wait, formatWait(waitMillis))
    AccessProblem.InviteRequired -> stringResource(R.string.onboarding_error_invite_required)
    AccessProblem.InviteInvalid -> stringResource(R.string.onboarding_error_invite_invalid)
    AccessProblem.NameTaken -> stringResource(R.string.onboarding_error_name_taken)
    AccessProblem.NameInvalid -> stringResource(R.string.onboarding_error_name_invalid)
}
