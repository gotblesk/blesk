package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.locksDataStore: DataStore<Preferences> by preferencesDataStore(name = "chat_locks")

/** How many unread messages a locked chat shows in the list. */
enum class LockCounter { Number, Dot, Hidden }

enum class LockPreview { Hidden, Shown }

/** What a notification from a locked chat says: everything, only that something came, or nothing at all. */
enum class LockNotify { Full, Anonymous, None }

/** Whether a locked chat turns up in search, «Поделиться», shortcuts and widgets. */
enum class LockListing { Hide, Show }

/** When an opened chat closes again: as soon as you leave it, a minute later, or when the app goes to the background. */
enum class LockRelock { OnExit, AfterMinute, OnBackground }

enum class LockUnlock { CodeOrBiometric, CodeOnly }

/** The app's own code, or a separate one only for locks. */
enum class LockCode { App, Separate }

/** How locked chats behave; the defaults are the ones in the brief. */
@Serializable
data class LockRules(
    val counter: LockCounter = LockCounter.Dot,
    val preview: LockPreview = LockPreview.Hidden,
    val notify: LockNotify = LockNotify.Anonymous,
    val search: LockListing = LockListing.Hide,
    val share: LockListing = LockListing.Hide,
    val relock: LockRelock = LockRelock.OnExit,
    val unlock: LockUnlock = LockUnlock.CodeOrBiometric,
    val code: LockCode = LockCode.App,
)

/** One chat's own rules; a null field follows the general settings. */
@Serializable
data class LockOverrides(
    val counter: LockCounter? = null,
    val preview: LockPreview? = null,
    val notify: LockNotify? = null,
    val search: LockListing? = null,
    val share: LockListing? = null,
    val relock: LockRelock? = null,
    val unlock: LockUnlock? = null,
    val code: LockCode? = null,
) {
    fun over(base: LockRules) = LockRules(
        counter = counter ?: base.counter,
        preview = preview ?: base.preview,
        notify = notify ?: base.notify,
        search = search ?: base.search,
        share = share ?: base.share,
        relock = relock ?: base.relock,
        unlock = unlock ?: base.unlock,
        code = code ?: base.code,
    )

    val isEmpty: Boolean get() = this == LockOverrides()
}

/**
 * Which chats are under a lock — any chat, «Избранное», or the whole archive ([ARCHIVE]) — and how
 * they behave. The lock guards the interface; what is on the disk is encrypted whether locked or not.
 */
@Serializable
data class ChatLocks(
    val rules: LockRules = LockRules(),
    val locked: Set<String> = emptySet(),
    val overrides: Map<String, LockOverrides> = emptyMap(),
) {
    fun isLocked(id: String) = id in locked

    /** A room's channels share the room's lock. */
    fun covers(conversationId: String) = isLocked(conversationId) || isLocked(conversationId.substringBefore(':'))

    fun rulesFor(id: String): LockRules = (overrides[id] ?: overrides[id.substringBefore(':')])?.over(rules) ?: rules

    companion object {
        /** The whole archive, as one locked thing. */
        const val ARCHIVE = "@archive"
    }
}

class ChatLockStore(context: Context) {
    private val store = context.applicationContext.locksDataStore
    private val json = Json { ignoreUnknownKeys = true }

    val locks: Flow<ChatLocks> = store.data
        .map { prefs -> prefs[STATE]?.let { runCatching { json.decodeFromString(ChatLocks.serializer(), it) }.getOrNull() } ?: ChatLocks() }
        .distinctUntilChanged()

    suspend fun update(transform: (ChatLocks) -> ChatLocks) {
        store.edit { prefs ->
            val current = prefs[STATE]?.let { runCatching { json.decodeFromString(ChatLocks.serializer(), it) }.getOrNull() } ?: ChatLocks()
            val next = transform(current)
            prefs[STATE] = json.encodeToString(ChatLocks.serializer(), next.copy(overrides = next.overrides.filterValues { !it.isEmpty }))
        }
    }

    suspend fun setLocked(id: String, locked: Boolean) = update { locks ->
        locks.copy(
            locked = if (locked) locks.locked + id else locks.locked - id,
            overrides = if (locked) locks.overrides else locks.overrides - id,
        )
    }

    private companion object {
        val STATE = stringPreferencesKey("state")
    }
}
