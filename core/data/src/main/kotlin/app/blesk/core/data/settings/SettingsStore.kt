package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.blesk.shared.appearance.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Which world the app talks to. Demo until a server is set. */
sealed interface AppMode {
    data object Demo : AppMode
    data class Server(val address: String) : AppMode
}

/**
 * Non-secret preferences. Nothing here is sensitive: message data and keys live in the
 * encrypted database, never in DataStore.
 */
class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    val appearance: Flow<Appearance> = store.data
        .map { prefs -> prefs[APPEARANCE]?.let(::decodeAppearance) ?: Appearance() }
        .distinctUntilChanged()

    val mode: Flow<AppMode> = store.data
        .map { prefs -> prefs[SERVER]?.takeIf { it.isNotBlank() }?.let { AppMode.Server(it) } ?: AppMode.Demo }
        .distinctUntilChanged()

    val debugUnlocked: Flow<Boolean> = store.data.map { it[DEBUG_UNLOCKED] ?: false }.distinctUntilChanged()

    suspend fun updateAppearance(transform: (Appearance) -> Appearance) {
        store.edit { prefs ->
            val current = prefs[APPEARANCE]?.let(::decodeAppearance) ?: Appearance()
            prefs[APPEARANCE] = json.encodeToString(Appearance.serializer(), transform(current).normalized())
        }
    }

    suspend fun setDebugUnlocked(unlocked: Boolean) {
        store.edit { it[DEBUG_UNLOCKED] = unlocked }
    }

    private fun decodeAppearance(raw: String): Appearance? = try {
        json.decodeFromString(Appearance.serializer(), raw).normalized()
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    private companion object {
        val APPEARANCE = stringPreferencesKey("appearance")
        val SERVER = stringPreferencesKey("server")
        val DEBUG_UNLOCKED = booleanPreferencesKey("debug_unlocked")
    }
}
