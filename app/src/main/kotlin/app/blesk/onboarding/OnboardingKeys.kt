package app.blesk.onboarding

import androidx.navigation3.runtime.NavKey
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.network.ServerAddress
import kotlinx.serialization.Serializable

/** A [ServerAddress] a screen's key can carry: keys are saved with the screens' state. */
@Serializable
data class ServerRef(val url: String, val pin: String? = null) {
    fun address(): ServerAddress = ServerAddress(url, pin)

    companion object {
        fun of(address: ServerAddress): ServerRef = ServerRef(address.url, address.pin)
    }
}

/** The first screen, until the demo is chosen or an account made. */
@Serializable
data object WelcomeKey : NavKey

/** «Другой сервер»; [scanned] is a server's QR code read elsewhere, put into the field. */
@Serializable
data class OtherServerKey(val scanned: ServerRef? = null) : NavKey

@Serializable
data class InviteKey(val server: ServerRef) : NavKey

/** [invite] is the code the invite step took, or null where the server lets people in without one. */
@Serializable
data class UsernameKey(val server: ServerRef, val invite: String?) : NavKey

/** One step of setting up the account just made. */
@Serializable
data class SetupKey(val step: SetupStep) : NavKey

/**
 * Back to the invite step already open, or onto it: a second invite link never stacks another.
 * An invite step open for another server becomes one for [server].
 */
internal fun MutableList<NavKey>.openSignUp(server: ServerRef) {
    val at = indexOfFirst { it is InviteKey }
    if (at < 0) {
        add(InviteKey(server))
        return
    }
    while (lastIndex > at) removeAt(lastIndex)
    if (this[at] != InviteKey(server)) this[at] = InviteKey(server)
}
