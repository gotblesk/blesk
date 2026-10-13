package app.blesk

import android.content.Context
import android.net.ConnectivityManager
import app.blesk.core.crypto.DatabaseKey
import app.blesk.core.crypto.phrase.PhraseKeys
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.KeyManager
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.crypto.signal.SignalStore
import app.blesk.core.data.R
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.Privacy
import app.blesk.core.messaging.AccountManager
import app.blesk.core.messaging.OwnProfile
import app.blesk.core.messaging.RecoverResult
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerChatRepository
import app.blesk.core.network.BleskApi
import app.blesk.core.network.LinkPreviews
import app.blesk.core.network.ServerAddress
import app.blesk.core.network.Socket
import kotlinx.coroutines.flow.StateFlow

/** The encrypted database, keys and connection used in server mode. */
internal class ServerStack(
    private val context: Context,
    private val container: AppContainer,
    val address: ServerAddress,
) {
    private val database: BleskDatabase by lazy { BleskDatabase.open(context, DatabaseKey(context).obtain()) }
    private val store: SignalStore by lazy { SignalStore(database.signal()) }
    private val keys: KeyManager by lazy { KeyManager(store) }
    val accounts: AccountManager by lazy { AccountManager(store, keys) }
    val api = BleskApi(address)

    /** Creates the account bound to [phrase]; runs off the main thread. */
    suspend fun register(username: String, phrase: PhraseKeys): RegisterResult = accounts.register(api, username, phrase)

    /** Takes over the account of [phrase] on this phone; runs off the main thread. */
    suspend fun recover(phrase: PhraseKeys): RecoverResult = accounts.recover(api, phrase)

    fun repository(
        username: String,
        privacy: StateFlow<Privacy>,
        profile: StateFlow<OwnProfile>,
        keepArchived: StateFlow<Boolean>,
    ) =
        ServerChatRepository(
            db = database,
            api = api,
            socket = Socket(api, container.scope),
            crypto = MessageCrypto(store) { username },
            groupCrypto = GroupCrypto(store) { username },
            keys = keys,
            accounts = accounts,
            media = container.media,
            me = username,
            scope = container.scope,
            privacy = { privacy.value },
            profile = { profile.value },
            labels = context.resources::attachmentLabel,
            log = container.eventLog,
            savedTitle = context.getString(R.string.demo_saved_title),
            network = container.network,
            keepArchived = { keepArchived.value },
            linkPreview = LinkPreviews()::forText,
            autoDownload = { kind -> container.autoDownload.value.allows(kind, context.isMetered()) },
        ).also { repository -> store.onIdentityChanged = repository::onKeyChanged }

    companion object {
        fun from(context: Context, container: AppContainer, mode: AppMode.Server) =
            ServerStack(context, container, ServerAddress(mode.url, mode.pin))
    }
}

/** Mobile data, or a Wi-Fi the system knows to be paid by the byte. */
private fun Context.isMetered(): Boolean = getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
