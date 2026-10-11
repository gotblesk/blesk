package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
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

    /** Per-chat theme overrides, keyed by conversation id. */
    val chatThemes: Flow<Map<String, ThemeSpec>> = store.data
        .map { prefs -> prefs[CHAT_THEMES]?.let(::decodeChatThemes).orEmpty() }
        .distinctUntilChanged()

    /** Bumped whenever the chat wallpaper file changes, so screens reload it. */
    val wallpaperVersion: Flow<Long> = store.data.map { it[WALLPAPER_VERSION] ?: 0L }.distinctUntilChanged()

    suspend fun setChatTheme(conversationId: String, theme: ThemeSpec?) {
        store.edit { prefs ->
            val current = prefs[CHAT_THEMES]?.let(::decodeChatThemes).orEmpty()
            val next = if (theme == null) current - conversationId else current + (conversationId to theme.normalized())
            prefs[CHAT_THEMES] = json.encodeToString(chatThemesSerializer, next)
        }
    }

    suspend fun bumpWallpaper() {
        store.edit { it[WALLPAPER_VERSION] = (it[WALLPAPER_VERSION] ?: 0L) + 1 }
    }

    private fun decodeChatThemes(raw: String): Map<String, ThemeSpec>? = try {
        json.decodeFromString(chatThemesSerializer, raw)
    } catch (_: SerializationException) {
        null
    } catch (_: IllegalArgumentException) {
        null
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
        val CHAT_THEMES = stringPreferencesKey("chat_themes")
        val WALLPAPER_VERSION = longPreferencesKey("wallpaper_version")
        val chatThemesSerializer = MapSerializer(String.serializer(), ThemeSpec.serializer())
    }
}
