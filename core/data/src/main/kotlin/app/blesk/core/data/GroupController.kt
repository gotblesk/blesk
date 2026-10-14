package app.blesk.core.data

import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import kotlinx.coroutines.flow.Flow

/** Closed groups and rooms; available where there is a real server. */
interface GroupController {
    /** Your username, to tell your own role in a group. */
    val me: String

    fun group(id: String): Flow<GroupState?>

    suspend fun createGroup(kind: GroupKind, title: String, members: List<String>): String

    suspend fun rename(groupId: String, title: String): Boolean

    suspend fun addMembers(groupId: String, usernames: List<String>): Boolean

    suspend fun removeMember(groupId: String, username: String): Boolean

    suspend fun setRole(groupId: String, username: String, role: GroupRole): Boolean

    suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean): Boolean

    suspend fun removeChannel(groupId: String, channelId: String): Boolean

    /** Any change an owner or admin may make, checked by [app.blesk.shared.protocol.GroupPolicy]; false when not allowed. */
    suspend fun change(groupId: String, change: GroupChange): Boolean

    /** Returns the invite link when turned on. */
    suspend fun setInvite(groupId: String, enabled: Boolean): String?

    suspend fun leave(groupId: String)

    /** Sends a join request for an invite link; false if the link is not valid. */
    suspend fun join(link: String): Boolean
}
