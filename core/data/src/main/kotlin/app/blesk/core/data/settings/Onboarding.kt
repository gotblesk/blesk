package app.blesk.core.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.blesk.shared.protocol.TextSafety
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The steps after an account is created, in order; the one saved is where an interrupted setup goes on. */
enum class SetupStep { Profile, Phrase, Code, Theme, Glint }

/** What the app opens with. */
sealed interface StartState {
    /** No account, and the demo has not been chosen. */
    data object Welcome : StartState

    data object Demo : StartState

    /** [pendingSetup] is the step an unfinished setup stopped at; null opens the chats. */
    data class Account(val pendingSetup: SetupStep?) : StartState
}

/** The last server typed or scanned on the server step; [pin] is the hash of its key, not a secret. */
data class LastServer(val url: String, val pin: String?)

/** A setup step means nothing without an account and is ignored. */
internal fun startStateOf(mode: AppMode, choseDemo: Boolean, step: SetupStep?): StartState = when {
    mode is AppMode.Server -> StartState.Account(step)
    choseDemo -> StartState.Demo
    else -> StartState.Welcome
}

/**
 * How the app first starts and what the sign-up remembers. Its keys sit in the settings' own store, so
 * entering an account drops the demo's settings and its own marks in the write that saves the account.
 */
class OnboardingStore(settings: SettingsStore) {
    private val store = settings.store

    val start: Flow<StartState> = store.data
        .map { prefs -> startStateOf(prefs.appMode(), prefs[DEMO_CHOSEN] ?: false, prefs[SETUP_STEP]?.let(::stepOf)) }
        .distinctUntilChanged()

    val lastServer: Flow<LastServer?> = store.data
        .map { prefs -> prefs[LAST_SERVER_URL]?.takeIf { it.isNotBlank() }?.let { LastServer(it, prefs[LAST_SERVER_PIN]) } }
        .distinctUntilChanged()

    /** The handle of the account's server's own channel, as the server last named it; null when it names none. */
    val officialChannel: Flow<String?> = store.data.map { it[OFFICIAL_CHANNEL] }.distinctUntilChanged()

    /** The official channel the setup was asked to subscribe to, until the server has taken the subscription. */
    val subscribePending: Flow<String?> = store.data.map { it[SUBSCRIBE_PENDING] }.distinctUntilChanged()

    suspend fun chooseDemo() {
        store.edit { it[DEMO_CHOSEN] = true }
    }

    /** Null once the setup is done. */
    suspend fun setSetupStep(step: SetupStep?) {
        store.edit { if (step != null) it[SETUP_STEP] = step.name else it.remove(SETUP_STEP) }
    }

    /**
     * Ends the setup and, with [subscribeTo], keeps the official channel to subscribe to in the same write: a
     * phone stopped right after still subscribes at its next start.
     */
    suspend fun finishSetup(subscribeTo: String?) {
        store.edit {
            it.remove(SETUP_STEP)
            if (subscribeTo != null) it[SUBSCRIBE_PENDING] = subscribeTo
        }
    }

    suspend fun setOfficialChannel(handle: String?) {
        store.edit { if (handle != null) it[OFFICIAL_CHANNEL] = handle else it.remove(OFFICIAL_CHANNEL) }
    }

    /** The server has taken the subscription to [handle]; one asked for since is still to come. */
    suspend fun subscribed(handle: String) {
        store.edit { if (it[SUBSCRIBE_PENDING] == handle) it.remove(SUBSCRIBE_PENDING) }
    }

    suspend fun rememberServer(url: String, pin: String?) {
        store.edit {
            it[LAST_SERVER_URL] = url
            if (pin != null) it[LAST_SERVER_PIN] = pin else it.remove(LAST_SERVER_PIN)
        }
    }

    /**
     * Saves the display name cleaned like every name. A name typed only of characters that show nothing is
     * refused rather than saved as an empty one: false, and the name before stays. An empty name is allowed.
     */
    suspend fun setProfileName(name: String): Boolean {
        val clean = TextSafety.clean(name).take(Profile.MAX_NAME)
        if (name.isNotBlank() && !TextSafety.hasVisible(clean)) return false
        store.edit { it[SettingsStore.PROFILE_NAME] = clean }
        return true
    }

    /**
     * Saves [account] as the world this phone opens and, in the same write, drops what belonged to the demo
     * world: chat themes and mention-only chats keyed by demo chat ids, which are real usernames, the demo
     * profile and the mark that the demo was chosen. Appearance, privacy, notifications and the other
     * preferences are the person's own and stay. The name and photo stay only for a new account, whose setup
     * offers them again; an account brought back by its phrase has its own. [setup] is the step its setup
     * starts at, and [officialChannel] the channel its server named, which its glint step offers; the one of
     * the server before, and a subscription to it still waiting, are forgotten. One write, so the app is never
     * seen in the account with the demo's settings, nor on the welcome screen between the two, nor in a new
     * account that skipped its setup.
     */
    suspend fun enterAccount(account: AppMode.Server, keepNameAndPhoto: Boolean, setup: SetupStep?, officialChannel: String? = null) {
        store.edit { prefs ->
            DEMO_KEYS.forEach { prefs.remove(it) }
            if (officialChannel != null) prefs[OFFICIAL_CHANNEL] = officialChannel else prefs.remove(OFFICIAL_CHANNEL)
            prefs.remove(SUBSCRIBE_PENDING)
            if (!keepNameAndPhoto) {
                prefs.remove(SettingsStore.PROFILE_NAME)
                prefs.remove(SettingsStore.PROFILE_PHOTO)
            }
            if (setup != null) prefs[SETUP_STEP] = setup.name else prefs.remove(SETUP_STEP)
            prefs.putServer(account)
        }
    }

    private fun stepOf(name: String): SetupStep? = SetupStep.entries.firstOrNull { it.name == name }

    private companion object {
        val DEMO_CHOSEN = booleanPreferencesKey("onboarding_demo")
        val SETUP_STEP = stringPreferencesKey("setup_step")
        val LAST_SERVER_URL = stringPreferencesKey("last_server_url")
        val LAST_SERVER_PIN = stringPreferencesKey("last_server_pin")
        val OFFICIAL_CHANNEL = stringPreferencesKey("official_channel")
        val SUBSCRIBE_PENDING = stringPreferencesKey("subscribe_pending")
        val DEMO_KEYS: List<Preferences.Key<*>> = listOf(
            SettingsStore.CHAT_THEMES,
            SettingsStore.MENTIONS_ONLY,
            SettingsStore.PROFILE_USERNAME,
            SettingsStore.PROFILE_BIO,
            SettingsStore.PROFILE_LINKS,
            SettingsStore.PROFILE_HEADER,
            SettingsStore.PROFILE_HEADER_PHOTO,
            DEMO_CHOSEN,
        )
    }
}
