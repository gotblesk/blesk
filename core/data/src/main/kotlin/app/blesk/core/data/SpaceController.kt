package app.blesk.core.data

import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.coroutines.flow.Flow

/** Why creating a public space did not work. */
enum class SpaceResult { Ok, HandleTaken, HandleInvalid, Failed }

/** Public channels and open rooms: not end-to-end encrypted, always shown matte and labelled. */
interface SpaceController {
    /** The space a conversation belongs to, if it is one. */
    fun space(conversationId: String): Flow<SpaceInfo?>

    fun isSpace(conversationId: String): Boolean

    suspend fun searchSpaces(query: String): List<SpaceInfo>

    /** Joins and returns the conversation id to open. */
    suspend fun joinSpace(handle: String): String?

    suspend fun createSpace(kind: SpaceKind, handle: String, title: String, about: String): Pair<SpaceResult, String?>

    suspend fun updateSpace(handle: String, title: String, about: String): Boolean

    suspend fun leaveSpace(handle: String)

    suspend fun deleteSpace(handle: String): Boolean

    suspend fun spaceMembers(handle: String): List<SpaceMember>

    suspend fun setSpaceRole(handle: String, username: String, role: SpaceRole): Boolean

    suspend fun ban(handle: String, username: String): Boolean

    suspend fun addSpaceChannel(handle: String, title: String): Boolean

    suspend fun removeSpaceChannel(handle: String, channel: String): Boolean
}
