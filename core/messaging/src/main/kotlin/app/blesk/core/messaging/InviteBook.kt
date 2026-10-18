package app.blesk.core.messaging

import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.InviteInfo
import app.blesk.shared.protocol.InviteList

/** How asking the server for one more invite went. */
sealed interface NewInvite {
    data class Made(val invite: InviteInfo) : NewInvite

    /** Every invite the person may hold is out, and none of them is used or expired yet. */
    data object Limit : NewInvite

    /** Too many asked for in a short time; a minute later it works again. */
    data object TooOften : NewInvite
    data object Failed : NewInvite
}

/** The signed-in person's invites to the server: who came by them and how many more can be made. */
class InviteBook(private val api: BleskApi) {
    /** Null when the server could not be asked, so a failure is never shown as «no invites». */
    suspend fun list(): InviteList? = runCatching { api.listInvites() }.getOrNull()

    suspend fun create(): NewInvite = runCatching { api.createInvite() }.fold(
        onSuccess = { NewInvite.Made(it) },
        onFailure = { error ->
            when ((error as? ApiException)?.code) {
                LIMIT -> NewInvite.Limit
                RATE_LIMITED -> NewInvite.TooOften
                else -> NewInvite.Failed
            }
        },
    )

    private companion object {
        const val LIMIT = "invite_limit"
        const val RATE_LIMITED = "rate_limited"
    }
}
