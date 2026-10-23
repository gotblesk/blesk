package app.blesk.core.design.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.blesk.core.design.theme.LocalClock
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameProblem
import app.blesk.shared.protocol.UsernameVerdict
import app.blesk.shared.protocol.Usernames
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/** What is known about the name in a @username field. */
@Immutable
sealed interface UsernameStatus {
    /** Nothing typed yet. */
    data object Empty : UsernameStatus

    /** The name the account already has. */
    data object Yours : UsernameStatus

    /** Being typed, or waiting for the server. */
    data object Checking : UsernameStatus

    /** A rule the name still breaks, such as being too short; nobody was asked. */
    data class Problem(val problem: UsernameProblem) : UsernameStatus

    data class Free(val name: String) : UsernameStatus

    /** Someone has the name; [suggestions] are names like it that the server says are free. */
    data class Taken(val name: String, val suggestions: ImmutableList<String>) : UsernameStatus

    /** Kept for blesk itself, or for bots. Nobody can take it, so it is refused like a taken one. */
    data class Reserved(val name: String, val suggestions: ImmutableList<String>) : UsernameStatus

    /** The server could not be asked. */
    data object Unknown : UsernameStatus
}

/** The status the server's [check] of [name] gives; null when it could not be asked. */
internal fun statusOf(name: String, check: UsernameCheck?): UsernameStatus {
    if (check == null) return UsernameStatus.Unknown
    val suggestions = check.suggestions.toImmutableList()
    return when (check.verdict) {
        UsernameVerdict.FREE -> UsernameStatus.Free(name)
        UsernameVerdict.TAKEN -> UsernameStatus.Taken(name, suggestions)
        UsernameVerdict.RESERVED -> UsernameStatus.Reserved(name, suggestions)
        // The server's rules may be newer than the app's: a name it refuses that breaks none of ours is kept back.
        UsernameVerdict.INVALID -> statusOf(name, Usernames.problem(name) ?: UsernameProblem.Reserved, suggestions)
        // A server older than verdicts says only whether the name is free.
        else -> if (check.available) UsernameStatus.Free(name) else UsernameStatus.Taken(name, suggestions)
    }
}

/** The status of a name breaking [problem]: one kept back for blesk or bots is refused, the rest is still being typed. */
internal fun statusOf(name: String, problem: UsernameProblem, suggestions: ImmutableList<String> = persistentListOf()): UsernameStatus =
    when (problem) {
        UsernameProblem.Reserved, UsernameProblem.BotName -> UsernameStatus.Reserved(name, suggestions)
        else -> UsernameStatus.Problem(problem)
    }

/** The name [status] refuses, if it refuses one. */
internal fun refusedName(status: UsernameStatus): String? = when (status) {
    is UsernameStatus.Taken -> status.name
    is UsernameStatus.Reserved -> status.name
    else -> null
}

/** Whether settling on [next] is a refusal to answer, once per name: going back to [lastRefused] stays quiet. */
internal fun refuseOn(next: UsernameStatus, lastRefused: String?): Boolean =
    refusedName(next).let { it != null && it != lastRefused }

/**
 * The server's recent answers by name, so typing a name again or taking an offered one asks
 * nothing. An answer is kept for [ttlMillis]; past [capacity] names the least recently used goes.
 */
internal class UsernameCache(
    private val capacity: Int = CAPACITY,
    private val ttlMillis: Long = TTL_MILLIS,
    private val clock: () -> Long,
) {
    private class Entry(val check: UsernameCheck, val at: Long)

    private val entries = object : LinkedHashMap<String, Entry>(capacity, LOAD_FACTOR, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean = size > capacity
    }

    operator fun get(name: String): UsernameCheck? {
        val entry = entries[name] ?: return null
        if (clock() - entry.at < ttlMillis) return entry.check
        entries.remove(name)
        return null
    }

    /** Keeps [check] of [name], and every name it offers as free. */
    fun put(name: String, check: UsernameCheck) {
        val now = clock()
        check.suggestions.forEach { offered ->
            entries[offered] = Entry(UsernameCheck(available = true, verdict = UsernameVerdict.FREE, username = offered), now)
        }
        entries[name] = Entry(check, now)
    }

    private companion object {
        const val CAPACITY = 32
        const val TTL_MILLIS = 60_000L
        const val LOAD_FACTOR = 0.75f
    }
}

/**
 * Whether the name in a @username field can be had. A name is asked about only once typing
 * pauses, and only the latest one; a name breaking a rule is not asked about at all, and a
 * recent answer is reused.
 */
@Stable
class UsernameChecks internal constructor(private val own: String?, private val cache: UsernameCache) {
    var status: UsernameStatus by mutableStateOf(UsernameStatus.Empty)
        private set

    private var shown = ""

    /** An answer about [name] that came another way, such as sign-up refusing it as taken: kept, and shown while the field holds [name]. */
    fun learn(name: String, check: UsernameCheck) {
        cache.put(name, check)
        if (name == shown) status = statusOf(name, check)
    }

    /** Settles on [name], the field's text now; cancelled when the text changes again. */
    internal suspend fun follow(name: String, ask: suspend (String) -> UsernameCheck?) {
        shown = name
        val known = when {
            name.isEmpty() -> UsernameStatus.Empty
            name == own -> UsernameStatus.Yours
            else -> cache[name]?.let { statusOf(name, it) }
        }
        if (known != null) {
            status = known
            return
        }
        status = UsernameStatus.Checking
        delay(CHECK_PAUSE_MILLIS)
        val problem = Usernames.problem(name)
        status = if (problem != null) {
            statusOf(name, problem)
        } else {
            val check = ask(name)
            if (check != null) cache.put(name, check)
            statusOf(name, check)
        }
    }
}

/**
 * Follows the text of [input]. [own] is the account's current name, if it has one. [ask] asks
 * the server about a name and gives null when it cannot.
 */
@Composable
fun rememberUsernameChecks(input: UsernameInput, own: String? = null, ask: suspend (String) -> UsernameCheck?): UsernameChecks {
    val clock = LocalClock.current
    val checks = remember(own, clock) { UsernameChecks(own, UsernameCache { clock().toEpochMilli() }) }
    val latestAsk by rememberUpdatedState(ask)
    LaunchedEffect(checks, input) {
        snapshotFlow { input.text }.collectLatest { name -> checks.follow(name) { latestAsk(it) } }
    }
    return checks
}

/** Typing usually goes on within this long; a pause longer than it is when a name is asked about. */
private const val CHECK_PAUSE_MILLIS = 400L
