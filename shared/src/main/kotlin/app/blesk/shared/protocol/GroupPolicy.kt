package app.blesk.shared.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64

/**
 * What someone may do in a group or room. Members of a group get [MEMBER_DEFAULT]; admins get
 * their own share of [ADMIN_ALL] (all of it unless trimmed) and every right beyond it; the owner
 * may do everything. In a room, roles add their rights on top of what every member has.
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

        /**
         * What every admin may do whatever members may: writing, sending and speaking, and the rest a room role may
         * carry. An admin stands above every role and gives roles these rights; one who lost them with the members
         * could neither give such a role nor give members back what they switched off.
         */
        val ADMIN_ALWAYS = entries.toSet() - ADMIN_ALL
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

    /** How many channels a room holds at most. */
    const val MAX_CHANNELS = 20

    /**
     * How far ahead of this phone's state one from anyone but the owner may be. A phone that missed a few states still
     * takes the next; steps no larger keep the version from being run to its end by any number of states. A phone
     * that missed more takes the owner's next state, which follows every other whatever its version.
     */
    const val MAX_VERSION_STEP = 100L
    private const val CHANNEL_SEPARATOR = ':'

    fun can(state: GroupState, username: String, right: Right): Boolean {
        val member = state.members.firstOrNull { it.username == username } ?: return false
        return when (member.role) {
            GroupRole.Owner -> true
            GroupRole.Admin -> right in (member.rights ?: Right.ADMIN_ALL) || right in Right.ADMIN_ALWAYS || right in state.memberRights
            GroupRole.Member -> right in state.memberRights || state.roles.any { it.id in member.roles && right in it.rights }
        }
    }

    /** Owners, admins, and members whose roles let them change something: their states are taken by others. */
    fun manages(state: GroupState, username: String): Boolean =
        state.isAdmin(username) || MANAGING.any { can(state, username, it) }

    /** Whether [username] may write in [conversationId]: the group itself or one of its channels, `group:channel`. */
    fun canWrite(state: GroupState, username: String, conversationId: String): Boolean {
        if (!can(state, username, Right.Write)) return false
        if (channelPart(conversationId).isEmpty()) return true
        val channel = channelOf(state, conversationId) ?: return false
        return sees(state, username, channel) && (!channel.adminsOnly || state.isAdmin(username))
    }

    /** The channel [conversationId] names; null for the group or room itself, and for a channel [state] does not have. */
    fun channelOf(state: GroupState, conversationId: String): GroupChannel? {
        val id = channelPart(conversationId).ifEmpty { return null }
        return state.channels.firstOrNull { it.id == id }
    }

    /** Whether [username] may read [conversationId]: a member reads the group itself and every channel they see. */
    fun reads(state: GroupState, username: String, conversationId: String): Boolean {
        if (state.roleOf(username) == null) return false
        if (channelPart(conversationId).isEmpty()) return true
        return channelOf(state, conversationId)?.let { sees(state, username, it) } ?: false
    }

    /** Everyone who may read [conversationId], in member order: the only ones a message there is for. */
    fun readers(state: GroupState, conversationId: String): List<String> =
        state.members.map { it.username }.filter { reads(state, it, conversationId) }

    /**
     * Which of our sender keys a message in [conversationId] is encrypted with: "" for the group, the room and its
     * channels open to all, the channel's id for one hidden from any role. Decided by the channel alone, not by who
     * reads it now: a channel closed to anyone never shares the key the whole room holds, even while all still read it.
     */
    fun keyScope(state: GroupState, conversationId: String): String =
        channelOf(state, conversationId)?.takeIf { it.hiddenFrom.isNotEmpty() }?.id.orEmpty()

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

    /**
     * A channel closed to every role [username] has is not shown to them; owners and admins see all. Only the room's
     * roles count: an id no role has any more, or not yet, opens nothing.
     */
    fun sees(state: GroupState, username: String, channel: GroupChannel): Boolean {
        val member = state.members.firstOrNull { it.username == username } ?: return false
        if (member.role != GroupRole.Member || channel.hiddenFrom.isEmpty()) return true
        val roles = member.roles.filter { id -> state.roles.any { it.id == id } } + EVERYONE
        return roles.any { it !in channel.hiddenFrom }
    }

    /**
     * Whether [incoming], a state from [sender], is one [sender] could reach from [current] by changes [apply] takes:
     * the same group of the same kind, which no change turns into another, as a room made a group would lose every
     * channel and their messages on every phone; who sees what, [admitsVisibility]; who may do what, [admitsRights];
     * and what else changed, [admitsEdits].
     */
    fun admits(current: GroupState, incoming: GroupState, sender: String): Boolean =
        incoming.id == current.id && incoming.kind == current.kind &&
            admitsVisibility(current, incoming, sender) && admitsRights(current, incoming, sender) && admitsEdits(current, incoming, sender)

    /**
     * Whether [incoming] follows [current]: made after more of the owner's states, or after as many and later. However
     * far others carry [GroupState.version], the owner's next state is newer, and one built before it is older.
     */
    fun newer(incoming: GroupState, current: GroupState): Boolean =
        incoming.ownerVersion > current.ownerVersion ||
            (incoming.ownerVersion == current.ownerVersion && incoming.version > current.version)

    /** [next], the state [actor] made from [state], one version on; and one of the owner's on, if [actor] is the owner. */
    fun versioned(state: GroupState, actor: String, next: GroupState): GroupState = next.copy(
        version = state.version + 1,
        ownerVersion = if (state.roleOf(actor) == GroupRole.Owner) state.ownerVersion + 1 else state.ownerVersion,
    )

    /**
     * Whether [incoming], a state from [sender], shows anyone a channel [current] hides from them only where [sender]
     * may open it. Rights and ranks come from [current], the state this phone trusts; closing needs no right. A state
     * that skips versions is judged as one change by [sender].
     */
    fun admitsVisibility(current: GroupState, incoming: GroupState, sender: String): Boolean =
        opensOnlyWithRight(current, incoming, sender) &&
            deletesOnlyWhatItSees(current, incoming, sender) &&
            hiddenKept(current, incoming, sender) &&
            newRolesStayClosed(current, incoming, sender) &&
            adminsMadeWithRight(current, incoming, sender) &&
            rolesGivenWithRight(current, incoming, sender)

    private fun opensOnlyWithRight(current: GroupState, incoming: GroupState, sender: String): Boolean =
        incoming.channels.all { channel ->
            val old = current.channels.firstOrNull { it.id == channel.id }
            (old != null && channel.hiddenFrom.containsAll(old.hiddenFrom)) || mayOpen(current, sender, channel.id)
        }

    /**
     * A channel goes only by one who may make it and sees it. Deleted by one it is hidden from, it could come back
     * under its own id and open to them, and those who see it would go on writing in it, now for one more.
     */
    private fun deletesOnlyWhatItSees(current: GroupState, incoming: GroupState, sender: String): Boolean =
        current.channels.all { channel -> incoming.channels.any { it.id == channel.id } || mayOpen(current, sender, channel.id) }

    /**
     * A channel hidden from [sender] stays as it is: what it is, its name, who writes in it and who sees it, but for
     * the closing to new roles [saveRole] does. Made a voice channel or shut to more people, it would take its messages
     * off the phones of those who see it, by one who never saw them.
     */
    private fun hiddenKept(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val arranged = arranged(current, incoming)
        return current.channels.filter { !sees(current, sender, it) }.all { channel -> arranged.firstOrNull { it.id == channel.id } == channel }
    }

    /**
     * A new role is closed out of every channel closed to everyone, as [apply] makes it, unless [sender] may open
     * that channel anyway.
     */
    private fun newRolesStayClosed(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val fresh = incoming.roles.filter { role -> current.roles.none { it.id == role.id } }
        if (fresh.isEmpty()) return true
        val closed = incoming.channels.filter { EVERYONE in it.hiddenFrom }
        return can(current, sender, Right.Roles) &&
            fresh.all { role -> closed.all { role.id in it.hiddenFrom || mayOpen(current, sender, it.id) } }
    }

    /** Whether [sender] may make a channel, or show it to more people: only one they see themselves. */
    private fun mayOpen(current: GroupState, sender: String, channelId: String): Boolean =
        can(current, sender, Right.Channels) && current.channels.none { it.id == channelId && !sees(current, sender, it) }

    /** Admins see every channel: a new one is made only by an admin who may add admins, as [appoints] decides. */
    private fun adminsMadeWithRight(current: GroupState, incoming: GroupState, sender: String): Boolean =
        (current.isAdmin(sender) && can(current, sender, Right.AddAdmins)) ||
            incoming.members.none { incoming.isAdmin(it.username) && !current.isAdmin(it.username) }

    /**
     * Every role someone gains is one [sender] could give them, as [assign] decides, with the rights and channels
     * [incoming] gives it. A role new in [incoming] ranks where [incoming] puts it; [newRolesStayClosed] has already
     * checked it opens nothing.
     */
    private fun rolesGivenWithRight(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val own = rank(current, sender)
        val assigns = can(current, sender, Right.AssignRoles)
        return incoming.members.all { member ->
            val had = current.members.firstOrNull { it.username == member.username }?.roles.orEmpty()
            member.roles.filter { it !in had }.all { id ->
                val roleRank = roleRank(current, id) ?: roleRank(incoming, id)
                assigns && roleRank != null && roleRank > own && rank(current, member.username) > own &&
                    incoming.roles.filter { it.id == id }.all { handsOut(current, sender, it, incoming.channels) }
            }
        }
    }

    /**
     * Whether [incoming], a state from [sender], raises no one beyond what [apply] lets [sender] do, judged by the
     * rights and ranks [current] gives them: it is built on the owner's state ours is, so the owner's next one undoes
     * it, as [newer] orders them, and is not far ahead; only the owner hands the group over; rights come
     * only from those [sender] has; the roles at and above [sender]'s own stay as they are; an admin, once made,
     * changes only by the owner, or downward by themselves; and no one is removed, or loses a role, but by one who may
     * do that to them.
     */
    fun admitsRights(current: GroupState, incoming: GroupState, sender: String): Boolean =
        current.roleOf(sender) == GroupRole.Owner || (
            incoming.ownerVersion == current.ownerVersion &&
                withinReach(current, incoming) &&
                owners(incoming) == owners(current) &&
                memberRightsGiven(current, incoming, sender) &&
                rolesAboveKept(current, incoming, sender) &&
                roleRightsGiven(current, incoming, sender) &&
                adminsKept(current, incoming, sender) &&
                removedWithRight(current, incoming, sender) &&
                rolesTakenWithRight(current, incoming, sender)
            )

    /**
     * Whether [incoming], a state from [sender], changes the rest of the group only as [apply] and the joining of
     * newcomers let [sender]: the details, what every member may do, the invites and who joined by them, the bans,
     * the channels and the roles each only with the right to. Members keep their order, so the admin who keeps the
     * closed voice channels' keys is not someone else's to pick, and newcomers come after them; no one is there
     * twice. The journal gains at most [sender]'s own line, [journalGrown], unless [sender] may read it: two managers
     * who changed the group at once each made a state the other's does not follow, and the newer one, taken in place
     * of the other, has the lines its side wrote where ours has ours, [journalBeside]. The owner may do all of it.
     */
    fun admitsEdits(current: GroupState, incoming: GroupState, sender: String): Boolean {
        if (current.roleOf(sender) == GroupRole.Owner) return true
        val newcomers = incoming.members.filter { current.roleOf(it.username) == null }
        val needed = buildSet {
            if (details(incoming) != details(current) || incoming.memberRights != current.memberRights) add(Right.EditInfo)
            if (incoming.invites != current.invites || incoming.invite != current.invite || newcomers.isNotEmpty()) add(Right.Invite)
            if (incoming.banned.toSet() != current.banned.toSet()) add(Right.Ban)
            if (arranged(current, incoming) != current.channels) add(Right.Channels)
            if (incoming.roles != current.roles) add(Right.Roles)
        }
        val journal = journalGrown(current, incoming, sender) || (can(current, sender, Right.Journal) && journalBeside(current, incoming))
        return journal &&
            needed.all { can(current, sender, it) } &&
            newcomers.none { it.username in incoming.banned } &&
            isWellFormed(incoming) &&
            orderKept(current, incoming)
    }

    private fun details(state: GroupState) = Triple(state.title, state.description, state.hue)

    /** [incoming]'s channels without the closing to its new roles, which [saveRole] does with no right to arrange them. */
    private fun arranged(current: GroupState, incoming: GroupState): List<GroupChannel> {
        val fresh = incoming.roles.map { it.id }.filter { id -> current.roles.none { it.id == id } }.toSet()
        return incoming.channels.map { it.copy(hiddenFrom = it.hiddenFrom - fresh) }
    }

    /** No member, channel or role twice: each is found by its name or id, and a second one would go unchecked. */
    private fun isWellFormed(state: GroupState): Boolean =
        state.members.distinctBy { it.username }.size == state.members.size &&
            state.channels.distinctBy { it.id }.size == state.channels.size &&
            state.roles.distinctBy { it.id }.size == state.roles.size

    /** Those who stay keep their order, and newcomers follow them, as every change that adds someone puts them. */
    private fun orderKept(current: GroupState, incoming: GroupState): Boolean {
        val names = incoming.members.map { it.username }
        val stayed = names.filter { current.roleOf(it) != null }
        return names.take(stayed.size) == stayed && stayed == current.members.map { it.username }.filter { incoming.roleOf(it) != null }
    }

    /**
     * The journal is [current]'s, or [current]'s with one line more, [sender]'s own, the oldest pushed out past [MAX_LOG]
     * as [logged] does: no line is changed or taken out, and none is made up in someone else's name. A state that
     * skipped some is taken after them, each with its own line.
     */
    private fun journalGrown(current: GroupState, incoming: GroupState, sender: String): Boolean =
        incoming.log == current.log ||
            (incoming.log.lastOrNull()?.actor == sender && incoming.log.dropLast(1) == current.log.takeLast(MAX_LOG - 1))

    /**
     * Whether [incoming]'s journal was made beside ours: it has our lines up to where the two sides parted, the oldest
     * pushed out past [MAX_LOG] on either side, and then lines of its own in place of ours. What it lacks is the lines
     * of states made at the same time as its side's, never the owner's, whose states both sides are built on; and it
     * has at most one line more than it lacks, its sender's own, as each side wrote one line a state.
     */
    private fun journalBeside(current: GroupState, incoming: GroupState): Boolean {
        val ours = current.log
        val theirs = incoming.log
        val owners = owners(current)
        for (dropped in 1..ours.size) {
            if (ours[ours.size - dropped].actor in owners) return false
            val base = ours.subList(0, ours.size - dropped)
            for (added in 0..minOf(theirs.size, dropped + 1)) {
                if (parted(base, theirs.subList(0, theirs.size - added), dropped, ours.size, theirs.size)) return true
            }
        }
        return false
    }

    /**
     * Whether [head], what a journal of [theirsSize] lines keeps before its own, is [base], ours before the lines it
     * lacks: the same lines, but for the oldest one side pushed out and the other did not. Ours pushed out at most one
     * for each of the [dropped] lines it lacks, and only once full; theirs, likewise.
     */
    private fun parted(base: List<LogEntry>, head: List<LogEntry>, dropped: Int, oursSize: Int, theirsSize: Int): Boolean =
        if (head.size <= base.size) {
            head == base.takeLast(head.size) && (head.size == base.size || theirsSize == MAX_LOG)
        } else {
            head.takeLast(base.size) == base && oursSize == MAX_LOG && head.size - base.size <= dropped
        }

    /** Whether [actor] may remove or ban [username]: only with the right, and only someone below them. */
    fun removes(state: GroupState, actor: String, username: String): Boolean =
        can(state, actor, Right.Ban) && rank(state, username) > rank(state, actor)

    /** At most [MAX_VERSION_STEP] ahead, compared so that no version, however large, wraps around. */
    private fun withinReach(current: GroupState, incoming: GroupState) = incoming.version - MAX_VERSION_STEP <= current.version

    private fun owners(state: GroupState) = state.members.filter { it.role == GroupRole.Owner }.map { it.username }

    private fun memberRightsGiven(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val added = incoming.memberRights - current.memberRights
        return added.isEmpty() || (can(current, sender, Right.EditInfo) && gives(current, sender, added))
    }

    /** The roles at and above [sender]'s own keep their places, names, tints and rights, and no other role joins them. */
    private fun rolesAboveKept(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val own = rank(current, sender)
        fun above(state: GroupState) = state.roles.filter { role -> roleRank(state, role.id)?.let { it <= own } == true }
        return above(incoming) == above(current)
    }

    /** A role gains only rights [sender] has, and only if [sender] may change roles. */
    private fun roleRightsGiven(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val gained = incoming.roles.flatMapTo(mutableSetOf()) { role ->
            role.rights - current.roles.firstOrNull { it.id == role.id }?.rights.orEmpty()
        }
        return gained.isEmpty() || (can(current, sender, Right.Roles) && gives(current, sender, gained))
    }

    /**
     * Another admin stays as they were, and [sender] changes only downward, as [admin] decides; a new admin, made as
     * [adminsMadeWithRight] decides, gets only rights [sender] has.
     */
    private fun adminsKept(current: GroupState, incoming: GroupState, sender: String): Boolean =
        incoming.members.all { member ->
            val before = current.members.firstOrNull { it.username == member.username }?.let(::adminRights)
            val after = adminRights(member)
            when {
                after == before -> true
                member.username == sender -> after == null || before?.containsAll(after) == true
                before != null -> false
                else -> gives(current, sender, after.orEmpty())
            }
        }

    /**
     * Everyone [current] has and [incoming] does not was removed by [sender] as [removes] decides, or is [sender]
     * themselves, gone of their own accord. Someone else who left is already gone from [current]: a phone takes a
     * leave as it comes, and does not need a state to learn of it.
     */
    private fun removedWithRight(current: GroupState, incoming: GroupState, sender: String): Boolean =
        current.members.all { it.username == sender || incoming.roleOf(it.username) != null || removes(current, sender, it.username) }

    /**
     * A role comes off someone only as [assign] or [roles] take it: by one who may give it and stands above both it
     * and them, or with the role itself, deleted by one who may change roles and stands above it. An id that is no
     * role of [current] opens nothing and may go.
     */
    private fun rolesTakenWithRight(current: GroupState, incoming: GroupState, sender: String): Boolean {
        val own = rank(current, sender)
        val assigns = can(current, sender, Right.AssignRoles)
        val deleted = current.roles.map { it.id }.filter { id -> incoming.roles.none { it.id == id } }
        val deletes = can(current, sender, Right.Roles) && deleted.all { outranks(current, sender, it) }
        return (deleted.isEmpty() || deletes) && incoming.members.all { member ->
            val had = current.members.firstOrNull { it.username == member.username }?.roles.orEmpty()
            (had - member.roles.toSet()).all { id ->
                id in deleted || current.roles.none { it.id == id } ||
                    (assigns && outranks(current, sender, id) && rank(current, member.username) > own)
            }
        }
    }

    /** Where a role puts its holders in [rank]; null for an id that is not one of the room's roles. */
    private fun roleRank(state: GroupState, roleId: String): Int? =
        state.roles.indexOfFirst { it.id == roleId }.takeIf { it >= 0 }?.plus(2)

    private fun channelPart(conversationId: String) = conversationId.substringAfter(CHANNEL_SEPARATOR, "")

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
            // A right every member gets is a right given: the actor adds only those they have themselves.
            is GroupChange.MemberRights -> if (!allowed(Right.EditInfo) || !gives(state, actor, change.rights - state.memberRights)) {
                null
            } else {
                state.copy(memberRights = change.rights).logged(now, actor, LogAction.Rights)
            }
            is GroupChange.Admin -> admin(state, actor, change, now)
            is GroupChange.Remove -> if (!removes(state, actor, change.username)) null else {
                state.copy(members = state.members.filterNot { it.username == change.username }, epoch = state.epoch + 1)
                    .logged(now, actor, LogAction.Removed, change.username)
            }
            is GroupChange.Ban -> if (!removes(state, actor, change.username)) null else {
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
        return next?.let { versioned(state, actor, it) }
    }

    /**
     * Whether [actor] may make [username] an admin, change their rights or make them a member again. The owner may,
     * for anyone but themselves. Otherwise only an admin with the right, and only for a member or themselves: admins
     * stand level, so none changes another. A member with the right may not use it: an admin they made would stand
     * above them and see every channel, and they could make themselves one.
     */
    fun appoints(state: GroupState, actor: String, username: String): Boolean {
        val target = state.roleOf(username) ?: return false
        if (target == GroupRole.Owner || !can(state, actor, Right.AddAdmins)) return false
        return when (state.roleOf(actor)) {
            GroupRole.Owner -> true
            GroupRole.Admin -> target == GroupRole.Member || username == actor
            else -> false
        }
    }

    /** An admin's rights gain only what the actor has, and their own nothing at all; they may keep what they had. */
    private fun admin(state: GroupState, actor: String, change: GroupChange.Admin, now: Long): GroupState? {
        if (!appoints(state, actor, change.username)) return null
        val target = state.members.first { it.username == change.username }
        val added = change.rights.orEmpty() - adminRights(target).orEmpty()
        if (added.isNotEmpty() && (target.username == actor || !gives(state, actor, added))) return null
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
        // Only someone who sees a channel may change it: hidden from them, it stays as it is.
        fun arranges(id: String) = can(state, actor, Right.Channels) && state.channels.any { it.id == id && sees(state, actor, it) }
        return when (change) {
            is GroupChange.RenameChannel -> if (!arranges(change.id)) null else state.copy(
                channels = state.channels.map {
                    if (it.id != change.id) it else it.copy(
                        title = TextSafety.clean(change.title).take(MAX_TITLE).ifBlank { it.title },
                        description = TextSafety.clean(change.description, multiline = true).take(MAX_DESCRIPTION),
                    )
                },
            ).logged(now, actor, LogAction.Channel, change.id)
            is GroupChange.MoveChannel -> if (!arranges(change.id)) {
                null
            } else {
                state.copy(channels = state.channels.moved(change.id, change.to, GroupChannel::id))
            }
            is GroupChange.HideChannel -> if (!arranges(change.id)) {
                null
            } else {
                state.copy(
                    channels = state.channels.map { if (it.id == change.id) it.copy(hiddenFrom = change.fromRoles) else it },
                ).logged(now, actor, LogAction.Channel, change.id)
            }
            is GroupChange.AssignRole -> assign(state, actor, change, now)
            else -> roles(state, actor, change, now)
        }
    }

    /** Roles are made, changed, moved and deleted by someone who may change roles, and only below them. */
    private fun roles(state: GroupState, actor: String, change: GroupChange, now: Long): GroupState? {
        if (!can(state, actor, Right.Roles)) return null
        return when (change) {
            is GroupChange.SaveRole -> saveRole(state, actor, change.role, now)
            // From below the actor to below them: their own role and those above stay where they are.
            is GroupChange.MoveRole -> if (!outranks(state, actor, change.id)) {
                null
            } else {
                state.copy(roles = state.roles.moved(change.id, change.to, RoomRole::id)).takeIf { outranks(it, actor, change.id) }
            }
            is GroupChange.DeleteRole -> if (!outranks(state, actor, change.id)) null else state.copy(
                roles = state.roles.filterNot { it.id == change.id },
                members = state.members.map { it.copy(roles = it.roles - change.id) },
            ).logged(now, actor, LogAction.Role, change.id)
            else -> null
        }
    }

    /**
     * A role is made or changed only below the actor, and gains only rights they have; it keeps those it had. A new one
     * goes last, below whoever makes it unless they hold no role and so stand below every one. It starts closed out of
     * every channel closed to everyone: otherwise whoever may make roles and give them would open such a channel to
     * anyone, themselves too, by a role made a moment ago.
     */
    private fun saveRole(state: GroupState, actor: String, saved: RoomRole, now: Long): GroupState? {
        val role = saved.copy(title = TextSafety.clean(saved.title).take(MAX_TITLE))
        val old = state.roles.firstOrNull { it.id == role.id }
        if (!gives(state, actor, role.rights - old?.rights.orEmpty())) return null
        val next = if (old != null) {
            state.copy(roles = state.roles.map { if (it.id == role.id) role else it })
        } else {
            state.copy(
                roles = state.roles + role,
                channels = state.channels.map { if (EVERYONE in it.hiddenFrom) it.copy(hiddenFrom = it.hiddenFrom + role.id) else it },
            )
        }
        return next.takeIf { outranks(it, actor, role.id) }?.logged(now, actor, LogAction.Role, role.title)
    }

    /**
     * A role may be given only by someone above it, to someone below them, and only if it brings nothing the giver
     * lacks: otherwise an account of theirs would get a right or a channel they do not have.
     */
    private fun assign(state: GroupState, actor: String, change: GroupChange.AssignRole, now: Long): GroupState? {
        if (!can(state, actor, Right.AssignRoles)) return null
        val role = state.roles.firstOrNull { it.id == change.roleId } ?: return null
        if (!outranks(state, actor, role.id) || rank(state, change.username) <= rank(state, actor)) return null
        if (change.on && !handsOut(state, actor, role)) return null
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

    /**
     * Whether [actor] stands above the role [roleId], and so may change, move, delete and give it. The owner stands
     * above every role, admins above all the room's roles, a member above those below their highest one.
     */
    fun outranks(state: GroupState, actor: String, roleId: String): Boolean =
        state.roleOf(actor) == GroupRole.Owner || (roleRank(state, roleId) ?: return false) > rank(state, actor)

    /** Whether [actor] has every one of [rights]: no one hands on a right they do not have. */
    private fun gives(state: GroupState, actor: String, rights: Set<Right>): Boolean = rights.all { can(state, actor, it) }

    /**
     * Whether [actor] may hand [role] to someone: it brings no right [actor] lacks and shows no channel of [channels]
     * that [state] hides from them. A channel [state] does not have yet is one [actor] makes, and so sees.
     */
    private fun handsOut(state: GroupState, actor: String, role: RoomRole, channels: List<GroupChannel> = state.channels) =
        gives(state, actor, role.rights) &&
            channels.all { channel ->
                role.id in channel.hiddenFrom || state.channels.none { it.id == channel.id && !sees(state, actor, it) }
            }

    /** What [member] may do as an admin; null for the owner and for members. */
    private fun adminRights(member: GroupMember): Set<Right>? =
        if (member.role == GroupRole.Admin) member.rights ?: Right.ADMIN_ALL else null

    private fun GroupState.logged(now: Long, actor: String, action: LogAction, target: String = "") =
        copy(log = (log + LogEntry(now, actor, action, target)).takeLast(MAX_LOG))

    private fun <T> List<T>.moved(id: String, to: Int, key: (T) -> String): List<T> {
        val item = firstOrNull { key(it) == id } ?: return this
        val rest = filterNot { key(it) == id }.toMutableList()
        rest.add(to.coerceIn(0, rest.size), item)
        return rest
    }
}
