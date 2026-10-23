package app.blesk.navigation

import androidx.annotation.StringRes
import app.blesk.R
import app.blesk.core.data.ChatRepository
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.network.ServerAddress
import app.blesk.shared.protocol.ChannelLink
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.Invites
import app.blesk.shared.protocol.ProfileLink
import kotlinx.coroutines.flow.MutableStateFlow

/** Where a link points, whether it was opened from another app, scanned or pasted. */
internal sealed interface LinkTarget {
    /** An invite to register, `blesk.su/i/<code>`. */
    data class Registration(val code: String) : LinkTarget

    data class Profile(val username: String, val key: String?) : LinkTarget

    /** A public channel or open room. */
    data class Space(val handle: String) : LinkTarget

    /** An invite into a closed group or room; [link] is what the group's [admin] checks. */
    data class Group(val link: String, val admin: String) : LinkTarget

    /** The QR code a server's setup printed. */
    data class Server(val address: ServerAddress) : LinkTarget

    companion object {
        private const val SERVER_LINK = "blesk://server"

        private val readers: List<(String) -> LinkTarget?> = listOf(
            { link -> ProfileLink.parse(link)?.let { Profile(it.username, it.key) } },
            { link -> ChannelLink.parse(link)?.let(::Space) },
            { link -> InviteLink.parse(link)?.let { Group(link, it.admin) } },
            // A bare code is no link: a twelve-letter username would read as one.
            { link -> Invites.parse(link)?.takeUnless { Invites.isValid(link.lowercase()) }?.let(::Registration) },
            { link -> link.takeIf { it.startsWith(SERVER_LINK) }?.let(ServerAddress::parse)?.let(::Server) },
        )

        fun of(text: String): LinkTarget? = text.trim().let { link -> readers.firstNotNullOfOrNull { it(link) } }
    }
}

/** What came of following a link. */
internal enum class LinkOutcome { Opened, JoinRequested, NotFound, NeedsAccount, AlreadyRegistered, AlreadyConnected }

/** The notice that tells the person about [this]; null when the screen that opened says enough. */
@get:StringRes
internal val LinkOutcome.notice: Int?
    get() = when (this) {
        LinkOutcome.Opened -> null
        LinkOutcome.JoinRequested -> app.blesk.feature.chats.R.string.contacts_join_sent
        LinkOutcome.NotFound -> app.blesk.feature.chats.R.string.link_not_found
        LinkOutcome.NeedsAccount -> R.string.link_needs_account
        LinkOutcome.AlreadyRegistered -> R.string.link_invite_registered
        LinkOutcome.AlreadyConnected -> R.string.link_server_connected
    }

/** The screens a followed link may open. */
internal class LinkScreens(
    val conversation: (String) -> Unit,
    val profile: (String) -> Unit,
    /** Sign-up on blesk's own server at its invite step, where a waiting invite is already filled in. */
    val signUp: () -> Unit,
    /** The step of a server other than blesk's own, with its address filled in. */
    val otherServer: (ServerAddress) -> Unit,
)

/**
 * Follows [target] in [account], the chats of the account, or null before there is one. Without
 * an account nobody can be asked: an invite to register is kept in [pendingInvite] for the
 * invite step, a server's code opens its step, and every other link opens sign-up, since it
 * needs an account first.
 */
internal suspend fun follow(
    target: LinkTarget,
    account: ChatRepository?,
    pendingInvite: MutableStateFlow<String?>,
    screens: LinkScreens,
): LinkOutcome = when {
    target is LinkTarget.Registration && account == null -> {
        pendingInvite.value = target.code
        screens.signUp()
        LinkOutcome.Opened
    }
    target is LinkTarget.Registration -> LinkOutcome.AlreadyRegistered
    target is LinkTarget.Server && account == null -> {
        if (target.address == ServerAddress.DEFAULT) screens.signUp() else screens.otherServer(target.address)
        LinkOutcome.Opened
    }
    target is LinkTarget.Server -> LinkOutcome.AlreadyConnected
    account == null -> {
        screens.signUp()
        LinkOutcome.NeedsAccount
    }
    else -> reach(target, account, screens.conversation, screens.profile)
}

/** Opens the person, channel or group behind [target] on the server; other targets lead nowhere here. */
internal suspend fun reach(
    target: LinkTarget,
    chats: ChatRepository,
    openConversation: (String) -> Unit,
    openProfile: (String) -> Unit,
): LinkOutcome = when (target) {
    is LinkTarget.Profile -> chats.openDirect(target.username, target.key).openedIn(openProfile)
    is LinkTarget.Space -> (chats as? SpaceController)?.joinSpace(target.handle).openedIn(openConversation)
    is LinkTarget.Group -> if ((chats as? GroupController)?.join(target.link) == true) LinkOutcome.JoinRequested else LinkOutcome.NotFound
    is LinkTarget.Registration, is LinkTarget.Server -> LinkOutcome.NotFound
}

/** What a public-space search may ask the server: a channel link its handle, and no other link at all. */
internal fun spaceQuery(text: String): String? = when (val target = LinkTarget.of(text)) {
    null -> text
    is LinkTarget.Space -> target.handle
    else -> null
}

private fun String?.openedIn(open: (String) -> Unit): LinkOutcome {
    val id = this ?: return LinkOutcome.NotFound
    open(id)
    return LinkOutcome.Opened
}
