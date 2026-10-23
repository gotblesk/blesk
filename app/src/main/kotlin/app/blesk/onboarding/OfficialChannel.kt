package app.blesk.onboarding

import app.blesk.core.data.Connection
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
 * again at each new connection, and at the next start while [store] keeps it waiting, until it does.
 */
internal suspend fun subscribeToOfficialChannel(
    handle: String,
    store: OnboardingStore,
    connection: Flow<Connection>,
    join: suspend (String) -> Boolean,
) {
    connection.distinctUntilChanged().filter { it == Connection.Online }.first { join(handle) }
    store.subscribed(handle)
}
