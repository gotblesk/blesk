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

    /** [pin] is the server key hash when it has no domain certificate. */
    data class Server(val url: String, val pin: String?, val username: String) : AppMode
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
        .map { prefs ->
            val url = prefs[SERVER]?.takeIf { it.isNotBlank() }
            val username = prefs[SERVER_USERNAME]?.takeIf { it.isNotBlank() }
            if (url != null && username != null) AppMode.Server(url, prefs[SERVER_PIN], username) else AppMode.Demo
        }
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

    val privacy: Flow<Privacy> = store.data.map { it.privacy() }.distinctUntilChanged()

    suspend fun updatePrivacy(transform: (Privacy) -> Privacy) {
        store.edit { prefs ->
            val next = transform(prefs.privacy())
            prefs[LOCK_DELAY] = next.lockDelay.name
            prefs[BIOMETRIC] = next.biometric
            prefs[SECURE_SCREEN] = next.secureScreen
            prefs[READ_RECEIPTS] = next.readReceipts
            prefs[TYPING_INDICATOR] = next.typingIndicator
        }
    }

    val notifications: Flow<NotificationSettings> = store.data.map { it.notifications() }.distinctUntilChanged()

    suspend fun updateNotifications(transform: (NotificationSettings) -> NotificationSettings) {
        store.edit { prefs ->
            val next = transform(prefs.notifications())
            prefs[NOTIFY] = next.enabled
            prefs[NOTIFY_PREVIEW] = next.preview.name
            prefs[ALWAYS_ONLINE] = next.alwaysOnline
        }
    }

    /** Enter sends instead of starting a new line; Shift+Enter still breaks the line. */
    val sendByEnter: Flow<Boolean> = store.data.map { it[SEND_BY_ENTER] ?: false }.distinctUntilChanged()

    suspend fun setSendByEnter(enabled: Boolean) {
        store.edit { it[SEND_BY_ENTER] = enabled }
    }

    /** Recently picked emoji, newest first. */
    val recentEmoji: Flow<List<String>> = store.data
        .map { prefs -> prefs[RECENT_EMOJI]?.split(EMOJI_SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty() }
        .distinctUntilChanged()

    suspend fun pickEmoji(emoji: String) {
        store.edit { prefs ->
            val current = prefs[RECENT_EMOJI]?.split(EMOJI_SEPARATOR).orEmpty()
            prefs[RECENT_EMOJI] = (listOf(emoji) + (current - emoji)).filter { it.isNotEmpty() }.take(MAX_RECENT_EMOJI)
                .joinToString(EMOJI_SEPARATOR)
        }
    }

    val profile: Flow<Profile> = store.data.map { prefs ->
        Profile(
            name = prefs[PROFILE_NAME].orEmpty(),
            username = prefs[PROFILE_USERNAME].orEmpty(),
            bio = prefs[PROFILE_BIO].orEmpty(),
            photoVersion = prefs[PROFILE_PHOTO] ?: 0L,
        )
    }.distinctUntilChanged()

    suspend fun updateProfile(name: String, username: String, bio: String) {
        store.edit {
            it[PROFILE_NAME] = name.trim().take(Profile.MAX_NAME)
            it[PROFILE_USERNAME] = Profile.normalizeUsername(username)
            it[PROFILE_BIO] = bio.trim().take(Profile.MAX_BIO)
        }
    }

    suspend fun bumpProfilePhoto() {
        store.edit { it[PROFILE_PHOTO] = (it[PROFILE_PHOTO] ?: 0L) + 1 }
    }

    suspend fun setServer(mode: AppMode.Server) {
        store.edit {
            it[SERVER] = mode.url
            it[SERVER_USERNAME] = mode.username
            if (mode.pin != null) it[SERVER_PIN] = mode.pin else it.remove(SERVER_PIN)
        }
    }

    suspend fun setDebugUnlocked(unlocked: Boolean) {
        store.edit { it[DEBUG_UNLOCKED] = unlocked }
    }

    private fun Preferences.notifications() = NotificationSettings(
        enabled = this[NOTIFY] ?: true,
        preview = this[NOTIFY_PREVIEW]?.let { name -> NotificationPreview.entries.firstOrNull { it.name == name } }
            ?: NotificationPreview.NameOnly,
        alwaysOnline = this[ALWAYS_ONLINE] ?: false,
    )

    private fun Preferences.privacy() = Privacy(
        lockDelay = this[LOCK_DELAY]?.let { name -> LockDelay.entries.firstOrNull { it.name == name } } ?: LockDelay.Immediately,
        biometric = this[BIOMETRIC] ?: false,
        secureScreen = this[SECURE_SCREEN] ?: false,
        readReceipts = this[READ_RECEIPTS] ?: true,
        typingIndicator = this[TYPING_INDICATOR] ?: true,
    )

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
        val SERVER_PIN = stringPreferencesKey("server_pin")
        val SERVER_USERNAME = stringPreferencesKey("server_username")
        val DEBUG_UNLOCKED = booleanPreferencesKey("debug_unlocked")
        val CHAT_THEMES = stringPreferencesKey("chat_themes")
        val WALLPAPER_VERSION = longPreferencesKey("wallpaper_version")
        val LOCK_DELAY = stringPreferencesKey("lock_delay")
        val BIOMETRIC = booleanPreferencesKey("biometric")
        val SECURE_SCREEN = booleanPreferencesKey("secure_screen")
        val READ_RECEIPTS = booleanPreferencesKey("read_receipts")
        val TYPING_INDICATOR = booleanPreferencesKey("typing_indicator")
        val NOTIFY = booleanPreferencesKey("notify")
        val NOTIFY_PREVIEW = stringPreferencesKey("notify_preview")
        val ALWAYS_ONLINE = booleanPreferencesKey("always_online")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_USERNAME = stringPreferencesKey("profile_username")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val PROFILE_PHOTO = longPreferencesKey("profile_photo")
        val SEND_BY_ENTER = booleanPreferencesKey("send_by_enter")
        val RECENT_EMOJI = stringPreferencesKey("recent_emoji")
        const val EMOJI_SEPARATOR = " "
        const val MAX_RECENT_EMOJI = 32
        val chatThemesSerializer = MapSerializer(String.serializer(), ThemeSpec.serializer())
    }
}
