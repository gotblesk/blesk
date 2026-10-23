package app.blesk

import android.content.Context
import android.net.ConnectivityManager
import app.blesk.core.crypto.phrase.PhraseKeys
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.crypto.signal.MessageCrypto
import app.blesk.core.data.R
import app.blesk.core.data.model.attachmentLabel
import app.blesk.core.data.settings.Privacy
import app.blesk.core.messaging.OwnProfile
import app.blesk.core.messaging.RecoverResult
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerChatRepository
import app.blesk.core.network.BleskApi
import app.blesk.core.network.LinkPreviews
import app.blesk.core.network.ServerAddress
import app.blesk.core.network.Socket
import kotlinx.coroutines.flow.StateFlow

/** The connection to the server at [address], over the one [vault] of this phone. */
internal class ServerStack(
    private val context: Context,
    private val container: AppContainer,
    val address: ServerAddress,
    val vault: ServerVault,
) {
    val api = BleskApi(address)

    /**
     * Creates the account bound to [phrase], by [invite] when the server asks for one, or with [resume]
     * finishes the one an attempt that broke off may have made; runs off the main thread.
     */
    suspend fun register(username: String, phrase: PhraseKeys, invite: String?, resume: Boolean): RegisterResult =
        vault.accounts.register(api, username, phrase, invite, resume)

    /** Takes over the account of [phrase] on this phone; runs off the main thread. */
    suspend fun recover(phrase: PhraseKeys): RecoverResult = vault.accounts.recover(api, phrase)

    fun repository(
        username: String,
        privacy: StateFlow<Privacy>,
        profile: StateFlow<OwnProfile>,
        keepArchived: StateFlow<Boolean>,
    ): ServerChatRepository {
        val scope = container.childScope()
        return ServerChatRepository(
            db = vault.database,
            api = api,
            socket = Socket(api, scope),
            crypto = MessageCrypto(vault.store) { username },
            groupCrypto = GroupCrypto(vault.store) { username },
            keys = vault.keys,
            accounts = vault.accounts,
            media = container.media,
            me = username,
            scope = scope,
            privacy = { privacy.value },
            profile = { profile.value },
            labels = context.resources::attachmentLabel,
            log = container.eventLog,
            savedTitle = context.getString(R.string.demo_saved_title),
            network = container.network,
            keepArchived = { keepArchived.value },
            linkPreview = LinkPreviews()::forText,
            autoDownload = { kind ->
                val metered = context.isMetered()
                // The economy mode keeps automatic downloads to Wi-Fi; a tap still downloads anything.
                container.autoDownload.value.allows(kind, metered) && !(metered && container.economy.effects.value.wifiOnlyMedia)
            },
        ).also { repository -> vault.store.onIdentityChanged = repository::onKeyChanged }
    }
}

/** Mobile data, or a Wi-Fi the system knows to be paid by the byte. */
private fun Context.isMetered(): Boolean = getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: true
