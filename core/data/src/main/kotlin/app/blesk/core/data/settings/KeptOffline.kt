package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.keptDataStore: DataStore<Preferences> by preferencesDataStore(name = "kept_offline")

/** A message whose file is kept for watching without the internet. */
data class OfflineKey(val conversationId: String, val messageId: String)

/**
 * «Сохранить для просмотра без интернета»: these files are downloaded whole, and the automatic
 * clean-up of old media leaves them alone. Only which messages, never what is in them.
 */
class KeptOffline(context: Context) {
    private val store = context.applicationContext.keptDataStore

    val keys: Flow<Set<OfflineKey>> = store.data
        .map { prefs -> prefs[KEPT].orEmpty().mapNotNull(::decode).toSet() }
        .distinctUntilChanged()

    suspend fun set(key: OfflineKey, kept: Boolean) {
        store.edit { prefs ->
            val current = prefs[KEPT].orEmpty()
            prefs[KEPT] = if (kept) current + encode(key) else current - encode(key)
        }
    }

    suspend fun clear() {
        store.edit { it.remove(KEPT) }
    }

    private fun encode(key: OfflineKey) = "${key.conversationId}$SEPARATOR${key.messageId}"

    private fun decode(value: String): OfflineKey? {
        val conversation = value.substringBefore(SEPARATOR, "")
        val message = value.substringAfter(SEPARATOR, "")
        return if (conversation.isEmpty() || message.isEmpty()) null else OfflineKey(conversation, message)
    }

    private companion object {
        val KEPT = stringSetPreferencesKey("kept")

        /** Ids never hold a line break, so it cannot be mistaken for part of one. */
        const val SEPARATOR = '\n'
    }
}
