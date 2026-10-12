package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.blesk.core.data.model.GLINT_REACTION
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

/** «Вибрация»: off, a lighter version of every pattern, or the full one. */
enum class VibrationLevel { Off, Quiet, Normal }

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
            prefs[LINK_PREVIEWS] = next.linkPreviews
            prefs[FINDABLE] = next.findable
            prefs[WHO_WRITES] = next.write.name
            prefs[WHO_CALLS] = next.calls.name
            prefs[WHO_ADDS] = next.groups.name
            prefs[WHO_SEES_ONLINE] = next.online.name
            prefs[INCOGNITO_KEYBOARD] = next.incognitoKeyboard
            prefs[WIPE_AFTER_TEN] = next.wipeAfterTen
            prefs[DEFAULT_TIMER] = next.defaultTimerSeconds
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

    /** Gesture hints already learned: each shows until the gesture has been done once. */
    val hintsSeen: Flow<Set<String>> = store.data.map { it[HINTS_SEEN].orEmpty() }.distinctUntilChanged()

    suspend fun markHint(hint: GestureHint) {
        store.edit { it[HINTS_SEEN] = it[HINTS_SEEN].orEmpty() + hint.name }
    }

    suspend fun resetHints() {
        store.edit { it.remove(HINTS_SEEN) }
    }

    /** What a double tap on a message puts on it: the «блик» unless an emoji was chosen instead. */
    val doubleTapReaction: Flow<String> = store.data.map { it[DOUBLE_TAP] ?: GLINT_REACTION }.distinctUntilChanged()

    suspend fun setDoubleTapReaction(symbol: String) {
        store.edit { it[DOUBLE_TAP] = symbol }
    }

    /** What chats lie on: `none`, `photo`, `light:N` or `solid:N`; null until chosen, when a picked photo shows. */
    val chatBackground: Flow<String?> = store.data.map { it[CHAT_BACKGROUND] }.distinctUntilChanged()

    suspend fun setChatBackground(value: String) {
        store.edit { it[CHAT_BACKGROUND] = value }
    }

    /** «Не возвращать из архива»: a new message leaves an archived chat in the archive. */
    val keepArchived: Flow<Boolean> = store.data.map { it[KEEP_ARCHIVED] ?: false }.distinctUntilChanged()

    suspend fun setKeepArchived(enabled: Boolean) {
        store.edit { it[KEEP_ARCHIVED] = enabled }
    }

    val proxy: Flow<ProxySetting> = store.data.map {
        ProxySetting(
            enabled = it[PROXY_ENABLED] ?: false,
            socks = it[PROXY_SOCKS] ?: true,
            host = it[PROXY_HOST].orEmpty(),
            port = it[PROXY_PORT] ?: 0,
        )
    }.distinctUntilChanged()

    suspend fun setProxy(proxy: ProxySetting) {
        store.edit {
            it[PROXY_ENABLED] = proxy.enabled
            it[PROXY_SOCKS] = proxy.socks
            it[PROXY_HOST] = proxy.host.trim()
            it[PROXY_PORT] = proxy.port
        }
    }

    /** Whether the app asks GitHub for newer builds when it starts. */
    val checkUpdates: Flow<Boolean> = store.data.map { it[CHECK_UPDATES] ?: true }.distinctUntilChanged()

    suspend fun setCheckUpdates(enabled: Boolean) {
        store.edit { it[CHECK_UPDATES] = enabled }
    }

    /** Media older than this many days leaves the phone on its own; 0 keeps it for good. */
    val keepMediaDays: Flow<Int> = store.data.map { it[KEEP_MEDIA_DAYS] ?: 0 }.distinctUntilChanged()

    suspend fun setKeepMediaDays(days: Int) {
        store.edit { it[KEEP_MEDIA_DAYS] = days }
    }

    /** The archive row folded into the top lens; pulling the list down opens it. */
    val archiveFolded: Flow<Boolean> = store.data.map { it[ARCHIVE_FOLDED] ?: false }.distinctUntilChanged()

    suspend fun setArchiveFolded(folded: Boolean) {
        store.edit { it[ARCHIVE_FOLDED] = folded }
    }

    val vibration: Flow<VibrationLevel> = store.data
        .map { prefs -> VibrationLevel.entries.firstOrNull { it.name == prefs[VIBRATION] } ?: VibrationLevel.Normal }
        .distinctUntilChanged()

    suspend fun setVibration(level: VibrationLevel) {
        store.edit { it[VIBRATION] = level.name }
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
            header = prefs[PROFILE_HEADER] ?: Profile.DEFAULT_HEADER,
            headerVersion = prefs[PROFILE_HEADER_PHOTO] ?: 0L,
        )
    }.distinctUntilChanged()

    suspend fun updateProfile(name: String, username: String, bio: String) {
        store.edit {
            it[PROFILE_NAME] = name.trim().take(Profile.MAX_NAME)
            it[PROFILE_USERNAME] = Profile.normalizeUsername(username)
            it[PROFILE_BIO] = bio.trim().take(Profile.MAX_BIO)
        }
    }

    suspend fun setProfileHeader(header: String) {
        store.edit { it[PROFILE_HEADER] = header }
    }

    suspend fun bumpProfileHeader() {
        store.edit { it[PROFILE_HEADER_PHOTO] = (it[PROFILE_HEADER_PHOTO] ?: 0L) + 1 }
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
        // Fifteen minutes was a choice before; the nearest one now is five.
        lockDelay = this[LOCK_DELAY]?.let(::lockDelayOf) ?: LockDelay.Immediately,
        biometric = this[BIOMETRIC] ?: false,
        secureScreen = this[SECURE_SCREEN] ?: false,
        readReceipts = this[READ_RECEIPTS] ?: true,
        typingIndicator = this[TYPING_INDICATOR] ?: true,
        linkPreviews = this[LINK_PREVIEWS] ?: true,
        findable = this[FINDABLE] ?: true,
        write = audience(WHO_WRITES, Audience.Everyone),
        calls = audience(WHO_CALLS, Audience.Everyone),
        groups = audience(WHO_ADDS, Audience.Everyone),
        online = audience(WHO_SEES_ONLINE, Audience.Contacts),
        incognitoKeyboard = this[INCOGNITO_KEYBOARD] ?: true,
        wipeAfterTen = this[WIPE_AFTER_TEN] ?: false,
        defaultTimerSeconds = this[DEFAULT_TIMER]?.takeIf { it in Privacy.TIMER_STEPS } ?: 0,
    )

    private fun lockDelayOf(name: String): LockDelay? =
        LockDelay.entries.firstOrNull { it.name == name } ?: LockDelay.FiveMinutes.takeIf { name == "FifteenMinutes" }

    private fun Preferences.audience(key: Preferences.Key<String>, fallback: Audience) =
        this[key]?.let { name -> Audience.entries.firstOrNull { it.name == name } } ?: fallback

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
        val FINDABLE = booleanPreferencesKey("findable")
        val WHO_WRITES = stringPreferencesKey("who_writes")
        val WHO_CALLS = stringPreferencesKey("who_calls")
        val WHO_ADDS = stringPreferencesKey("who_adds")
        val WHO_SEES_ONLINE = stringPreferencesKey("who_sees_online")
        val INCOGNITO_KEYBOARD = booleanPreferencesKey("incognito_keyboard")
        val WIPE_AFTER_TEN = booleanPreferencesKey("wipe_after_ten")
        val DEFAULT_TIMER = intPreferencesKey("default_timer")
        val TYPING_INDICATOR = booleanPreferencesKey("typing_indicator")
        val LINK_PREVIEWS = booleanPreferencesKey("link_previews")
        val NOTIFY = booleanPreferencesKey("notify")
        val NOTIFY_PREVIEW = stringPreferencesKey("notify_preview")
        val ALWAYS_ONLINE = booleanPreferencesKey("always_online")
        val PROFILE_NAME = stringPreferencesKey("profile_name")
        val PROFILE_USERNAME = stringPreferencesKey("profile_username")
        val PROFILE_BIO = stringPreferencesKey("profile_bio")
        val PROFILE_PHOTO = longPreferencesKey("profile_photo")
        val PROFILE_HEADER = stringPreferencesKey("profile_header")
        val PROFILE_HEADER_PHOTO = longPreferencesKey("profile_header_photo")
        val SEND_BY_ENTER = booleanPreferencesKey("send_by_enter")
        val KEEP_ARCHIVED = booleanPreferencesKey("keep_archived")
        val KEEP_MEDIA_DAYS = intPreferencesKey("keep_media_days")
        val PROXY_ENABLED = booleanPreferencesKey("proxy_enabled")
        val CHECK_UPDATES = booleanPreferencesKey("check_updates")
        val PROXY_SOCKS = booleanPreferencesKey("proxy_socks")
        val PROXY_HOST = stringPreferencesKey("proxy_host")
        val PROXY_PORT = intPreferencesKey("proxy_port")
        val CHAT_BACKGROUND = stringPreferencesKey("chat_background")
        val DOUBLE_TAP = stringPreferencesKey("double_tap_reaction")
        val HINTS_SEEN = stringSetPreferencesKey("hints_seen")
        val ARCHIVE_FOLDED = booleanPreferencesKey("archive_folded")
        val VIBRATION = stringPreferencesKey("vibration")
        val RECENT_EMOJI = stringPreferencesKey("recent_emoji")
        const val EMOJI_SEPARATOR = " "
        const val MAX_RECENT_EMOJI = 32
        val chatThemesSerializer = MapSerializer(String.serializer(), ThemeSpec.serializer())
    }
}
