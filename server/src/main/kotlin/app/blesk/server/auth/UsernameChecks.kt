package app.blesk.server.auth

import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameProblem
import app.blesk.shared.protocol.UsernameVerdict
import app.blesk.shared.protocol.Usernames
import java.util.Locale
import kotlin.random.Random

/**
 * Whether a name may be taken, asked while it is typed. The name is compared in small letters, so
 * «Egor» is «egor». Whenever it may not be taken, up to [SUGGESTIONS] free names like it come back
 * too. The name and its variants go to [taken] together, so a check is one question to the database.
 */
class UsernameChecks(
    private val taken: suspend (Collection<String>) -> Set<String>,
    private val random: Random = Random.Default,
) {
    suspend fun check(asked: String): UsernameCheck {
        if (asked.length > MAX_ASKED) return UsernameCheck(available = false, verdict = UsernameVerdict.INVALID)
        val name = asked.lowercase(Locale.ROOT)
        val problem = Usernames.problem(name)
        val candidates = Usernames.variants(name, random)
        val asking = if (problem == null) listOf(name) + candidates else candidates
        val held = if (asking.isEmpty()) emptySet() else taken(asking)
        val verdict = when (problem) {
            null -> if (name in held) UsernameVerdict.TAKEN else UsernameVerdict.FREE
            UsernameProblem.Reserved, UsernameProblem.BotName -> UsernameVerdict.RESERVED
            else -> UsernameVerdict.INVALID
        }
        if (verdict == UsernameVerdict.FREE) return UsernameCheck(available = true, verdict = verdict, username = name)
        return UsernameCheck(available = false, verdict = verdict, username = name, suggestions = (candidates - held).take(SUGGESTIONS))
    }

    companion object {
        const val SUGGESTIONS = 3

        /** Longer is no name anyone typed, so it costs no database question. */
        const val MAX_ASKED = 64
    }
}
