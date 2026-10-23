package app.blesk.server.spaces

import app.blesk.server.auth.AccountRepository
import app.blesk.shared.protocol.Usernames
import java.time.Instant

/**
 * The server's command line for its owner, run in place of the server: `official <owner>` makes the
 * channel @[HANDLE] the server's own, owned by that account, and `/v1/info` names it from then on.
 * Running it again changes nothing.
 */
data class OfficialCommand(val owner: String) {
    /** How the command ended, with the exit status the owner's scripts read. */
    enum class Outcome(val status: Int) {
        Made(0),
        Already(0),
        NoAccount(3),

        /** Another account owns a space at the handle, made before such handles were kept back. */
        HandleTaken(4),
    }

    suspend fun run(accounts: AccountRepository, spaces: SpaceRepository, now: Instant): Outcome {
        val account = accounts.find(owner) ?: return Outcome.NoAccount
        return when (spaces.makeOfficial(HANDLE, account.id, now)) {
            Officialness.Made -> Outcome.Made
            Officialness.Already -> Outcome.Already
            Officialness.HandleTaken -> Outcome.HandleTaken
        }
    }

    companion object {
        const val HANDLE = "blesk"
        const val USAGE = "usage: blesk-server official <owner-username>"

        /** Null when [args] are not this command; the caller then prints [USAGE]. */
        fun parse(args: List<String>): OfficialCommand? =
            args.takeIf { it.size == 2 && it[0] == "official" }?.get(1)?.takeIf(Usernames::isWellFormed)?.let(::OfficialCommand)
    }
}
