package app.blesk.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.messaging.AccountManager
import app.blesk.core.messaging.ProbeResult
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerTrouble
import app.blesk.core.network.ApiException
import app.blesk.core.network.ServerAddress
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.shared.protocol.ServerInfo
import app.blesk.shared.protocol.UsernameCheck
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Getting an account made: the server it is made on, what that server said of itself, the checks
 * of a name and the account's creation. One per app, like the app lock, so a server chosen on
 * its step is still the one when a link or another screen starts the sign-up.
 */
internal class SignUp(private val container: AppContainer) {
    /** blesk's own server unless another was chosen on «Другой сервер». */
    var server: ServerAddress by mutableStateOf(ServerAddress.DEFAULT)

    /** The server chosen on «Другой сервер» as the screens name it; null while it is blesk's own. */
    val otherServer: String?
        get() = server.takeIf { it != ServerAddress.DEFAULT }?.url?.removePrefix("https://")

    /** The invite the server last turned down, shown on the invite step while its code stays in the field. */
    var refusedInvite: RefusedInvite? by mutableStateOf(null)

    private val infos = ConcurrentHashMap<ServerAddress, ServerInfo>()

    /** What [address] said of itself the last time it answered a first look; null before that. */
    fun info(address: ServerAddress): ServerInfo? = infos[address]

    /** A first look at [address]: whether an account can be made there. Opens nothing on this phone. */
    suspend fun probe(address: ServerAddress): Probe = when (val result = AccountManager.probe(container.stackFor(address).api)) {
        is ProbeResult.Ready -> Probe.Ready(result.info).also { infos[address] = result.info }
        is ServerTrouble -> Probe.Failed(result.problem(address))
    }

    /** What [address] says of [name]; null when it could not be asked, and then the server decides at creation. */
    suspend fun checkName(address: ServerAddress, name: String): UsernameCheck? = try {
        container.stackFor(address).api.checkUsername(name)
    } catch (_: IOException) {
        null
    } catch (_: ApiException) {
        null
    }

    /**
     * Makes the account; on [RegisterResult.Ok] the app has moved to it and its setup has begun. The official
     * channel the server named on its first look is saved with the account, for the glint step to offer.
     */
    suspend fun create(address: ServerAddress, invite: String?, username: String): RegisterResult =
        container.connect(address, username, invite, officialChannel = info(address)?.officialChannel)

    /** The account the name step makes on [server] by [invite]. */
    fun account(server: ServerAddress, invite: String?): NewAccount = object : NewAccount {
        override val address = server

        override fun unfinishedName() = container.unfinishedName(server)

        override suspend fun check(name: String) = checkName(server, name)

        override suspend fun create(name: String) = this@SignUp.create(server, invite, name)
    }
}

/** An account to be made on one server, as the name step sees it. */
internal interface NewAccount {
    val address: ServerAddress

    /** The name of an attempt here that did not succeed: the server may have made its account, which is then this phone's. */
    fun unfinishedName(): String?

    /** What the server says of [name]; null when it could not be asked. */
    suspend fun check(name: String): UsernameCheck?

    suspend fun create(name: String): RegisterResult
}

/**
 * Keeps the server chosen for sign-up with the screens' saved state. The choice lives as long as the app, while
 * Android may close the app in the background and bring its screens back in a new one, on blesk's own server.
 */
@Composable
internal fun KeepSignUpServer(signUp: SignUp) {
    rememberSaveable(
        saver = Saver(
            save = { signUp.server.link() },
            // Written before any screen reads the server: this runs first in the app's first frame after the restart.
            restore = { link -> ServerAddress.parse(link)?.also { signUp.server = it } },
        ),
    ) { signUp.server }
}

/** The invite [code] the server refused, and why. */
internal class RefusedInvite(val code: String, val problem: AccessProblem)

/** What a first look at a server found. */
internal sealed interface Probe {
    data class Ready(val info: ServerInfo) : Probe

    data class Failed(val problem: AccessProblem) : Probe
}

/** How the entry screens tell of [this] trouble with the server at [address]. */
internal fun ServerTrouble.problem(address: ServerAddress): AccessProblem {
    // For a server of the person's own choosing, its address or its QR code is the first thing to check.
    val other = address != ServerAddress.DEFAULT
    return when (this) {
        is ServerTrouble.TooManyAttempts -> AccessProblem.TooManyAttempts(retryAfterMillis ?: UNSAID_WAIT_MILLIS)
        ServerTrouble.CertificateMismatch -> AccessProblem.CertificateMismatch
        ServerTrouble.CertificateUntrusted -> AccessProblem.CertificateUntrusted(other)
        ServerTrouble.AppTooOld -> AccessProblem.AppTooOld
        ServerTrouble.ServerTooOld -> AccessProblem.ServerTooOld
        ServerTrouble.NotBlesk -> AccessProblem.NotBlesk(other)
        ServerTrouble.Unreachable -> AccessProblem.Unreachable(other)
    }
}

/** Where sign-up on [server] begins: the invite step, unless the server said it lets people in without one. */
internal fun firstSignUpKey(server: ServerRef, info: ServerInfo): NavKey =
    if (info.invites == false) UsernameKey(server, invite = null) else InviteKey(server)

/**
 * Whether the invite step needs a code: it may be left empty only for a server that said it lets people in
 * without one, or an older one that does not say, which then has the last word. One never asked, as when an
 * invite link opened the step, needs one.
 */
internal fun inviteRequired(info: ServerInfo?): Boolean = info == null || info.invites == true

/** A server that asks to wait without saying how long is given a minute. */
private const val UNSAID_WAIT_MILLIS = 60_000L
