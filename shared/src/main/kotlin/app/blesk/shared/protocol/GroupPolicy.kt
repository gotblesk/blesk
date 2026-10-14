package app.blesk.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64

/**
 * What someone may do in a group or room. Members of a group get [MEMBER_DEFAULT]; admins get
 * their own set (all of [ADMIN_ALL] unless trimmed); the owner may do everything. In a room,
 * roles add their rights on top of what every member has.
 */
@Serializable
enum class Right {
    @SerialName("write")
    Write,

    @SerialName("media")
    Media,

    @SerialName("stickers")
    Stickers,

    @SerialName("links")
    Links,

    @SerialName("polls")
    Polls,

    @SerialName("invite")
    Invite,

    @SerialName("pin")
    Pin,

    @SerialName("edit_info")
    EditInfo,

    @SerialName("delete_others")
    DeleteOthers,

    @SerialName("ban")
    Ban,

    @SerialName("calls")
    Calls,

    @SerialName("add_admins")
    AddAdmins,

    @SerialName("channels")
    Channels,

    @SerialName("roles")
    Roles,

    @SerialName("journal")
    Journal,

    @SerialName("timeout")
    Timeout,

    @SerialName("assign_roles")
    AssignRoles,

    @SerialName("mention_all")
    MentionAll,

    @SerialName("connect")
    Connect,

    @SerialName("speak")
    Speak,

    @SerialName("screen")
    Screen,

    @SerialName("mute_others")
    MuteOthers,

    @SerialName("move")
    Move,
    ;

    companion object {
        val MEMBER_DEFAULT = setOf(Write, Media, Stickers, Links, Polls, Invite, Pin, Connect, Speak, Screen)
        val ADMIN_ALL =
            setOf(EditInfo, DeleteOthers, Ban, Invite, Pin, Calls, AddAdmins, Channels, Roles, Journal, Timeout, AssignRoles)
    }
}

/** A room role: its name, its tint (one of the palette's hues) and what it adds. */
@Serializable
data class RoomRole(val id: String, val title: String, val hue: Int = 0, val rights: Set<Right> = emptySet())

/** An invite link: its name, when it stops working, how many may come by it, and who came. */
@Serializable
data class GroupInvite(
    val code: String,
    val title: String = "",
    /** Epoch millis; null never runs out. */
    val expiresAt: Long? = null,
    /** How many may join by it; null for no limit. */
    val limit: Int? = null,
    val joined: List<String> = emptyList(),
    val revoked: Boolean = false,
) {
    fun usable(now: Long) = !revoked && (expiresAt == null || now < expiresAt) && (limit == null || joined.size < limit)

    companion object {
        private val random = SecureRandom()

        /** A new invite with an unguessable code, the same length the old single invite had. */
        fun fresh(title: String, expiresAt: Long?, limit: Int?): GroupInvite {
            val code = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(CODE_BYTES).also(random::nextBytes))
            return GroupInvite(code, TextSafety.clean(title).take(GroupPolicy.MAX_TITLE), expiresAt, limit)
        }

        private const val CODE_BYTES = 12
    }
}

/** One line of the journal: who did what to whom, and when. */
@Serializable
data class LogEntry(val at: Long, val actor: String, val action: LogAction, val target: String = "")

@Serializable
enum class LogAction {
    @SerialName("edited")
    Edited,

    @SerialName("rights")
    Rights,

    @SerialName("admin")
    Admin,

    @SerialName("removed")
    Removed,

    @SerialName("banned")
    Banned,

    @SerialName("unbanned")
    Unbanned,

    @SerialName("invite")
    Invite,

    @SerialName("revoked")
    Revoked,

    @SerialName("owner")
    Owner,

    @SerialName("channel")
    Channel,

    @SerialName("role")
    Role,
}

/** One change of a group or room, made by an owner or an admin. */
sealed interface GroupChange {
    data class Describe(val title: String? = null, val description: String? = null, val hue: Int? = null) : GroupChange
    data class MemberRights(val rights: Set<Right>) : GroupChange

    /** Makes [username] an admin with [rights], or a plain member again with null. */
    data class Admin(val username: String, val rights: Set<Right>?) : GroupChange
    data class Remove(val username: String) : GroupChange
    data class Ban(val username: String) : GroupChange
    data class Unban(val username: String) : GroupChange
    data class NewInvite(val invite: GroupInvite) : GroupChange
    data class Revoke(val code: String) : GroupChange
    data class TransferOwnership(val username: String) : GroupChange

    /** The owner deletes the group for everyone: the last state lists only them, so every other phone lets it go. */
    data object Dissolve : GroupChange
    data class RenameChannel(val id: String, val title: String, val description: String = "") : GroupChange
    data class MoveChannel(val id: String, val to: Int) : GroupChange
    data class HideChannel(val id: String, val fromRoles: Set<String>) : GroupChange
    data class SaveRole(val role: RoomRole) : GroupChange
    data class MoveRole(val id: String, val to: Int) : GroupChange
    data class DeleteRole(val id: String) : GroupChange
    data class AssignRole(val username: String, val roleId: String, val on: Boolean) : GroupChange
}

/**
 * Who may do what, decided the same way everywhere: on the phone that makes a change, in the demo,
 * and on every phone that receives the new state. [apply] returns the next state, or null when
 * [actor] is not allowed to make [change].
 */
object GroupPolicy {
    /** The role every room member has; a channel hidden from it is closed to those without another role. */
    const val EVERYONE = "everyone"
    const val MAX_LOG = 200
    const val MAX_TITLE = 64
    const val MAX_DESCRIPTION = 500

    fun can(state: GroupState, username: String, right: Right): Boolean {
        val member = state.members.firstOrNull { it.username == username } ?: return false
        return when (member.role) {
            GroupRole.Owner -> true
            GroupRole.Admin -> right in (member.rights ?: Right.ADMIN_ALL) || right in state.memberRights
            GroupRole.Member -> right in state.memberRights || state.roles.any { it.id in member.roles && right in it.rights }
        }
    }

    /** Owners, admins, and members whose roles let them change something: their states are taken by others. */
    fun manages(state: GroupState, username: String): Boolean =
        state.isAdmin(username) || MANAGING.any { can(state, username, it) }

    /** Whether [username] may write in [conversationId]: the group itself or one of its channels, `group:channel`. */
    fun canWrite(state: GroupState, username: String, conversationId: String): Boolean {
        if (!can(state, username, Right.Write)) return false
        val channel = state.channels.firstOrNull { it.id == conversationId.substringAfter(':', "") } ?: return true
        return sees(state, username, channel) && (!channel.adminsOnly || state.isAdmin(username))
    }

    private val MANAGING =
        setOf(Right.EditInfo, Right.Channels, Right.Roles, Right.Ban, Right.AssignRoles, Right.AddAdmins)

    /** Lower is higher: the owner 0, admins 1, then the room's roles in order, then everyone else. */
    fun rank(state: GroupState, username: String): Int {
        val member = state.members.firstOrNull { it.username == username } ?: return Int.MAX_VALUE
        return when (member.role) {
            GroupRole.Owner -> 0
            GroupRole.Admin -> 1
            GroupRole.Member -> state.roles.indexOfFirst { it.id in member.roles }.let { if (it < 0) Int.MAX_VALUE - 1 else it + 2 }
        }
    }

    /** A channel closed to every role [username] has is not shown to them; owners and admins see all. */
    fun sees(state: GroupState, username: String, channel: GroupChannel): Boolean {
        val member = state.members.firstOrNull { it.username == username } ?: return false
        if (member.role != GroupRole.Member || channel.hiddenFrom.isEmpty()) return true
        return (member.roles + EVERYONE).any { it !in channel.hiddenFrom }
    }

    @Suppress("CyclomaticComplexMethod")
    fun apply(state: GroupState, actor: String, change: GroupChange, now: Long): GroupState? {
        fun allowed(right: Right) = can(state, actor, right)
        val owner = state.roleOf(actor) == GroupRole.Owner
        val next = when (change) {
            is GroupChange.Describe -> if (!allowed(Right.EditInfo)) null else state.copy(
                title = change.title?.let(TextSafety::clean)?.take(MAX_TITLE)?.takeIf(TextSafety::hasVisible) ?: state.title,
                description = change.description?.let { TextSafety.clean(it, multiline = true).take(MAX_DESCRIPTION) } ?: state.description,
                hue = change.hue ?: state.hue,
            ).logged(now, actor, LogAction.Edited)
            is GroupChange.MemberRights -> if (!owner && !allowed(Right.EditInfo)) null else {
                state.copy(memberRights = change.rights).logged(now, actor, LogAction.Rights)
            }
            is GroupChange.Admin -> admin(state, actor, change, now)
            is GroupChange.Remove -> if (!allowed(Right.Ban) || rank(state, change.username) <= rank(state, actor)) null else {
                state.copy(members = state.members.filterNot { it.username == change.username }, epoch = state.epoch + 1)
                    .logged(now, actor, LogAction.Removed, change.username)
            }
            is GroupChange.Ban -> if (!allowed(Right.Ban) || rank(state, change.username) <= rank(state, actor)) null else {
                state.copy(
                    members = state.members.filterNot { it.username == change.username },
                    banned = (state.banned + change.username).distinct(),
                    epoch = state.epoch + 1,
                ).logged(now, actor, LogAction.Banned, change.username)
            }
            is GroupChange.Unban -> if (!allowed(Right.Ban)) null else {
                state.copy(banned = state.banned - change.username).logged(now, actor, LogAction.Unbanned, change.username)
            }
            is GroupChange.NewInvite -> if (!allowed(Right.Invite)) null else {
                state.copy(invites = state.invites + change.invite).logged(now, actor, LogAction.Invite, change.invite.title)
            }
            is GroupChange.Revoke -> if (!allowed(Right.Invite)) null else state.copy(
                invites = state.invites.map { if (it.code == change.code) it.copy(revoked = true) else it },
            ).logged(now, actor, LogAction.Revoked, change.code)
            is GroupChange.TransferOwnership -> transfer(state, actor, change.username, now)
            GroupChange.Dissolve -> if (!owner) null else state.copy(
                members = state.members.filter { it.username == actor },
                invites = emptyList(),
                invite = null,
                epoch = state.epoch + 1,
            )
            else -> rooms(state, actor, change, now)
        }
        return next?.copy(version = state.version + 1)
    }

    private fun admin(state: GroupState, actor: String, change: GroupChange.Admin, now: Long): GroupState? {
        if (!can(state, actor, Right.AddAdmins)) return null
        val target = state.members.firstOrNull { it.username == change.username } ?: return null
        if (target.role == GroupRole.Owner) return null
        val actorRights = if (state.roleOf(actor) == GroupRole.Owner) {
            Right.entries.toSet()
        } else {
            state.members.first { it.username == actor }.rights ?: Right.ADMIN_ALL
        }
        // An admin cannot hand out rights they do not have.
        if (change.rights != null && !actorRights.containsAll(change.rights)) return null
        val updated = if (change.rights == null) {
            target.copy(role = GroupRole.Member, rights = null)
        } else {
            target.copy(role = GroupRole.Admin, rights = change.rights)
        }
        return state.copy(members = state.members.map { if (it.username == target.username) updated else it })
            .logged(now, actor, LogAction.Admin, target.username)
    }

    private fun transfer(state: GroupState, actor: String, to: String, now: Long): GroupState? {
        if (state.roleOf(actor) != GroupRole.Owner || state.roleOf(to) == null || to == actor) return null
        return state.copy(
            members = state.members.map {
                when (it.username) {
                    to -> it.copy(role = GroupRole.Owner, rights = null)
                    actor -> it.copy(role = GroupRole.Admin, rights = Right.ADMIN_ALL)
                    else -> it
                }
            },
        ).logged(now, actor, LogAction.Owner, to)
    }

    private fun rooms(state: GroupState, actor: String, change: GroupChange, now: Long): GroupState? {
        val channels = can(state, actor, Right.Channels)
        val roles = can(state, actor, Right.Roles)
        return when (change) {
            is GroupChange.RenameChannel -> if (!channels) null else state.copy(
                channels = state.channels.map {
                    if (it.id != change.id) it else it.copy(
                        title = TextSafety.clean(change.title).take(MAX_TITLE).ifBlank { it.title },
                        description = TextSafety.clean(change.description, multiline = true).take(MAX_DESCRIPTION),
                    )
                },
            ).logged(now, actor, LogAction.Channel, change.id)
            is GroupChange.MoveChannel -> if (!channels) {
                null
            } else {
                state.copy(channels = state.channels.moved(change.id, change.to, GroupChannel::id))
            }
            is GroupChange.HideChannel -> if (!channels) null else state.copy(
                channels = state.channels.map { if (it.id == change.id) it.copy(hiddenFrom = change.fromRoles) else it },
            ).logged(now, actor, LogAction.Channel, change.id)
            is GroupChange.SaveRole -> if (!roles) null else {
                val role = change.role.copy(title = TextSafety.clean(change.role.title).take(MAX_TITLE))
                val exists = state.roles.any { it.id == role.id }
                state.copy(roles = if (exists) state.roles.map { if (it.id == role.id) role else it } else state.roles + role)
                    .logged(now, actor, LogAction.Role, role.title)
            }
            is GroupChange.MoveRole -> if (!roles) null else state.copy(roles = state.roles.moved(change.id, change.to, RoomRole::id))
            is GroupChange.DeleteRole -> if (!roles) null else state.copy(
                roles = state.roles.filterNot { it.id == change.id },
                members = state.members.map { it.copy(roles = it.roles - change.id) },
            ).logged(now, actor, LogAction.Role, change.id)
            is GroupChange.AssignRole -> assign(state, actor, change, now)
            else -> null
        }
    }

    /** A role may be given only by someone above it, to someone below them. */
    private fun assign(state: GroupState, actor: String, change: GroupChange.AssignRole, now: Long): GroupState? {
        if (!can(state, actor, Right.AssignRoles)) return null
        val roleRank = state.roles.indexOfFirst { it.id == change.roleId }.takeIf { it >= 0 }?.plus(2) ?: return null
        val own = rank(state, actor)
        if (roleRank <= own || rank(state, change.username) <= own) return null
        return state.copy(
            members = state.members.map {
                when {
                    it.username != change.username -> it
                    change.on -> it.copy(roles = (it.roles + change.roleId).distinct())
                    else -> it.copy(roles = it.roles - change.roleId)
                }
            },
        ).logged(now, actor, LogAction.Role, change.username)
    }

    private fun GroupState.logged(now: Long, actor: String, action: LogAction, target: String = "") =
        copy(log = (log + LogEntry(now, actor, action, target)).takeLast(MAX_LOG))

    private fun <T> List<T>.moved(id: String, to: Int, key: (T) -> String): List<T> {
        val item = firstOrNull { key(it) == id } ?: return this
        val rest = filterNot { key(it) == id }.toMutableList()
        rest.add(to.coerceIn(0, rest.size), item)
        return rest
    }
}
