package app.blesk.server.invites

import app.blesk.shared.protocol.Invites
import java.time.Instant

/**
 * The server's command line for its owner, run in place of the server: `invites` prints the unused
 * bootstrap invites as links, one per line, making [FIRST_BATCH] of them the first time ever;
 * `invites add <n>` makes n more first.
 */
sealed interface InviteCommand {
    /** Makes what the command makes, then returns the links to every unused bootstrap invite. */
    suspend fun run(invites: InviteRepository, now: Instant): List<String>

    data object Print : InviteCommand {
        override suspend fun run(invites: InviteRepository, now: Instant): List<String> {
            invites.createFirstBootstrap(FIRST_BATCH, now)
            return invites.unusedBootstrap().map(Invites::link)
        }
    }

    data class Add(val count: Int) : InviteCommand {
        override suspend fun run(invites: InviteRepository, now: Instant): List<String> {
            invites.createBootstrap(count, now)
            return invites.unusedBootstrap().map(Invites::link)
        }
    }

    companion object {
        const val FIRST_BATCH = 10
        val AddRange = 1..50
        const val USAGE = "usage: blesk-server invites | blesk-server invites add <1-50>"

        /** Null when [args] are not a command; the caller then prints [USAGE]. */
        fun parse(args: List<String>): InviteCommand? = when {
            args == listOf("invites") -> Print
            args.size == 3 && args[0] == "invites" && args[1] == "add" -> args[2].toIntOrNull()?.takeIf { it in AddRange }?.let(::Add)
            else -> null
        }
    }
}
