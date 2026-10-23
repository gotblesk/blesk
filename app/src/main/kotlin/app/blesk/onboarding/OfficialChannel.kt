package app.blesk.onboarding

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.blesk.core.data.Connection
import app.blesk.core.data.SpaceController
import app.blesk.core.data.settings.OnboardingStore
import app.blesk.shared.protocol.ServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first

/**
 * The official channel of the account's server, once [connection] is first online: the channel [info]
 * names is kept in [store], so the glint step offers it and «О приложении» leads to it, and a subscription
 * the setup asked for that the server has not taken yet is made now.
 */
internal suspend fun followOfficialChannel(
    store: OnboardingStore,
    connection: Flow<Connection>,
    info: suspend () -> ServerInfo?,
    join: suspend (String) -> Boolean,
) {
    connection.first { it == Connection.Online }
    info()?.let { store.setOfficialChannel(it.officialChannel) }
    store.subscribePending.first()?.let { subscribeToOfficialChannel(it, store, connection, join) }
}

/**
 * Joins the channel [handle] by [join] once [connection] is online. A join the server did not take is tried
 * again at each new connection, and at the next start while [store] keeps it waiting, until it does. Once the
 * person has joined or left the channel themselves it is theirs to decide, and nothing is joined for them.
 */
internal suspend fun subscribeToOfficialChannel(
    handle: String,
    store: OnboardingStore,
    connection: Flow<Connection>,
    join: suspend (String) -> Boolean,
) {
    connection.distinctUntilChanged().filter { it == Connection.Online }.first {
        store.subscribePending.first() != handle || join(handle)
    }
    store.settleSubscription(handle)
}

/**
 * «О приложении → Официальный канал»: the channel opens at once for someone in it. Anyone else is asked first,
 * as for a space link, since joining shows them among its members, and they may have unticked it at sign-up.
 */
@Stable
internal class OfficialChannelEntry(
    val handle: String,
    private val spaces: SpaceController,
    private val store: OnboardingStore,
    private val open: (conversationId: String) -> Unit,
) {
    /** Whether the question about joining is on screen. */
    var asking by mutableStateOf(false)
        private set

    suspend fun enter() {
        val id = SPACE_PREFIX + handle
        if (spaces.space(id).first() != null) open(id) else asking = true
    }

    /** Joins once the person said yes; false when the server has no such channel. */
    suspend fun join(): Boolean {
        asking = false
        val id = spaces.joinSpace(handle) ?: return false
        store.settleSubscription(handle)
        open(id)
        return true
    }

    fun dismiss() {
        asking = false
    }

    private companion object {
        const val SPACE_PREFIX = "space:"
    }
}
