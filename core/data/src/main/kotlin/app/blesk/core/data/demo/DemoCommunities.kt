package app.blesk.core.data.demo

import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.ME
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Preview
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.model.SendStatus
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

/** The conversations and messages of the demo, shared by the chats and the groups that live in them. */
internal class DemoWorld(seed: DemoSeed) {
    val conversations = MutableStateFlow(seed.conversations)
    val messages = MutableStateFlow(seed.messages.groupBy { it.conversationId })
}

/**
 * Groups, rooms, channels and open rooms of the demo. They are made and changed on the phone
 * only, with the demo people as members, so everything a server would do can be tried offline.
 */
internal class DemoCommunities(
    seeded: DemoSeed,
    private val world: DemoWorld,
    private val people: List<Contact>,
    private val texts: DemoCommunityTexts,
    private val scope: CoroutineScope,
    private val clock: () -> Instant,
) : GroupController, SpaceController {
    // The seeded group, room and channel are known from the start; their rows already are in the list.
    private val groups = MutableStateFlow(seeded.groups.associateBy { it.id })
    private val spaces = MutableStateFlow(seeded.spaces.associateBy { it.handle })

    override val me: String = ME

    override fun group(id: String): Flow<GroupState?> = groups.map { it[id] }

    override suspend fun createGroup(kind: GroupKind, title: String, members: List<String>): String {
        val id = UUID.randomUUID().toString()
        val channels = if (kind == GroupKind.Room) {
            listOf(GroupChannel(shortId(), texts.textChannel), GroupChannel(shortId(), texts.voiceChannel, GroupChannelKind.Voice))
        } else {
            emptyList()
        }
        val state = GroupState(
            id = id,
            kind = kind,
            title = title.trim().take(MAX_TITLE),
            version = 1,
            epoch = 0,
            members = listOf(GroupMember(me, GroupRole.Owner)) +
                members.distinct().filter { it != me }.map { GroupMember(it, GroupRole.Member) },
            channels = channels,
        )
        apply(state)
        // Someone from the demo answers, so the new group does not stay silent.
        val greeter = state.members.firstNotNullOfOrNull { member -> person(member.username)?.takeIf { member.username != me } }
        if (greeter != null) {
            val target = channels.firstOrNull { it.kind == GroupChannelKind.Text }?.let { "$id$SEPARATOR${it.id}" } ?: id
            scope.launch {
                delay(GREETING_MILLIS)
                post(target, greeter, texts.greeting)
            }
        }
        return id
    }

    override suspend fun rename(groupId: String, title: String) = change(groupId) { it.copy(title = title.trim().take(MAX_TITLE)) }

    override suspend fun addMembers(groupId: String, usernames: List<String>) = change(groupId) { state ->
        val fresh = usernames.distinct().filter { state.roleOf(it) == null }.map { GroupMember(it, GroupRole.Member) }
        state.copy(members = state.members + fresh)
    }

    override suspend fun removeMember(groupId: String, username: String) = change(groupId) { state ->
        val left = state.members.filterNot { it.username == username && it.role != GroupRole.Owner }
        state.copy(members = left, epoch = state.epoch + 1)
    }

    override suspend fun setRole(groupId: String, username: String, role: GroupRole) = change(groupId) { state ->
        state.copy(members = state.members.map { if (it.username == username && it.role != GroupRole.Owner) it.copy(role = role) else it })
    }

    override suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean) = change(groupId) {
        it.copy(channels = it.channels + GroupChannel(shortId(), title.trim().take(MAX_TITLE), kind, adminsOnly))
    }

    override suspend fun removeChannel(groupId: String, channelId: String) = change(groupId) { state ->
        state.copy(channels = state.channels.filterNot { it.id == channelId })
    }

    override suspend fun setInvite(groupId: String, enabled: Boolean): String? {
        val code = if (enabled) UUID.randomUUID().toString().replace("-", "").take(INVITE_LENGTH) else null
        change(groupId) { it.copy(invite = code) }
        return code?.let { InviteLink.format(groupId, me, it) }
    }

    override suspend fun leave(groupId: String) {
        groups.update { it - groupId }
        forget(groupId)
    }

    /** Invite links point at someone's server; the demo has none to ask. */
    override suspend fun join(link: String): Boolean = false

    // Public spaces

    override fun space(conversationId: String): Flow<SpaceInfo?> = spaces.map { all -> handleOf(conversationId)?.let(all::get) }

    override fun isSpace(conversationId: String) = handleOf(conversationId)?.let(spaces.value::containsKey) == true

    override suspend fun searchSpaces(query: String): List<SpaceInfo> {
        val needle = query.trim().removePrefix("@")
        if (needle.isEmpty()) return emptyList()
        return spaces.value.values.filter { it.handle.contains(needle, ignoreCase = true) || it.title.contains(needle, ignoreCase = true) }
    }

    override suspend fun joinSpace(handle: String): String? = (SPACE_PREFIX + handle).takeIf { handle in spaces.value }

    override suspend fun createSpace(kind: SpaceKind, handle: String, title: String, about: String): Pair<SpaceResult, String?> {
        if (!Spaces.isValidHandle(handle)) return SpaceResult.HandleInvalid to null
        if (handle in spaces.value || people.any { it.username == handle }) return SpaceResult.HandleTaken to null
        val channels = if (kind == SpaceKind.Room) listOf(SpaceChannel(shortId(), texts.textChannel)) else emptyList()
        val info = SpaceInfo(
            handle = handle,
            kind = kind,
            title = title.trim().take(Spaces.MAX_TITLE),
            about = about.trim().take(Spaces.MAX_ABOUT),
            members = 1,
            channels = channels,
            role = SpaceRole.Owner,
        )
        remember(info)
        return SpaceResult.Ok to SPACE_PREFIX + handle
    }

    override suspend fun updateSpace(handle: String, title: String, about: String): Boolean {
        val info = spaces.value[handle] ?: return false
        remember(info.copy(title = title.trim().take(Spaces.MAX_TITLE), about = about.trim().take(Spaces.MAX_ABOUT)))
        return true
    }

    override suspend fun leaveSpace(handle: String) {
        spaces.update { it - handle }
        forget(SPACE_PREFIX + handle)
    }

    override suspend fun deleteSpace(handle: String): Boolean {
        if (handle !in spaces.value) return false
        leaveSpace(handle)
        return true
    }

    override suspend fun spaceMembers(handle: String): List<SpaceMember>? =
        spaces.value[handle]?.let { listOf(SpaceMember(me, SpaceRole.Owner)) }

    /** You are the only member of a space made in the demo, so there is nobody to promote or ban. */
    override suspend fun setSpaceRole(handle: String, username: String, role: SpaceRole) = false

    override suspend fun ban(handle: String, username: String) = false

    override suspend fun addSpaceChannel(handle: String, title: String): Boolean {
        val info = spaces.value[handle]?.takeIf { it.kind == SpaceKind.Room } ?: return false
        remember(info.copy(channels = info.channels + SpaceChannel(shortId(), title.trim().take(Spaces.MAX_TITLE))))
        return true
    }

    override suspend fun removeSpaceChannel(handle: String, channel: String): Boolean {
        val info = spaces.value[handle] ?: return false
        if (info.channels.none { it.id == channel }) return false
        remember(info.copy(channels = info.channels.filterNot { it.id == channel }))
        return true
    }

    // Internals

    private fun handleOf(conversationId: String): String? =
        conversationId.takeIf { it.startsWith(SPACE_PREFIX) }?.removePrefix(SPACE_PREFIX)?.substringBefore(SEPARATOR)

    private fun person(username: String): Contact? = people.firstOrNull { it.username == username || it.id == username }

    private fun change(groupId: String, transform: (GroupState) -> GroupState): Boolean {
        val state = groups.value[groupId] ?: return false
        if (!state.isAdmin(me)) return false
        val next = transform(state)
        apply(next.copy(version = state.version + 1))
        return true
    }

    /** Keeps the group and its conversation rows (the group, or the room and its text channels) in step. */
    private fun apply(state: GroupState) {
        groups.update { it + (state.id to state) }
        val others = state.members.filter { it.username != me }.mapNotNull { person(it.username) }
        val textChannels = state.channels.filter { it.kind == GroupChannelKind.Text }
        val root = Conversation(
            id = state.id,
            kind = if (state.kind == GroupKind.Room) ConversationKind.Room else ConversationKind.Group,
            title = state.title,
            protection = Protection.EndToEnd,
            preview = null,
            updatedAt = clock(),
            memberCount = state.members.size,
            faces = others.take(FACES).toImmutableList(),
            channels = state.channels.map { channel ->
                val voice = channel.kind == GroupChannelKind.Voice
                RoomChannel(
                    id = if (voice) channel.id else "${state.id}$SEPARATOR${channel.id}",
                    title = channel.title,
                    kind = if (voice) RoomChannelKind.Voice else RoomChannelKind.Text,
                )
            }.toImmutableList(),
        )
        val children = textChannels.map { channel ->
            Conversation(
                "${state.id}$SEPARATOR${channel.id}", ConversationKind.Room, channel.title, Protection.EndToEnd, null, clock(),
                memberCount = state.members.size, parentId = state.id,
            )
        }
        store(root, children)
    }

    private fun remember(info: SpaceInfo) {
        spaces.update { it + (info.handle to info) }
        val rootId = SPACE_PREFIX + info.handle
        val kind = if (info.kind == SpaceKind.Channel) ConversationKind.Channel else ConversationKind.Room
        val ids = info.channels.map { "$rootId$SEPARATOR${it.id}" }
        val root = Conversation(
            rootId, kind, info.title, Protection.Open, null, clock(), memberCount = info.members,
            channels = if (info.kind == SpaceKind.Room) {
                info.channels.mapIndexed { index, channel ->
                    RoomChannel(ids[index], channel.title, RoomChannelKind.Text)
                }.toImmutableList()
            } else {
                persistentListOf()
            },
        )
        val children = if (info.kind == SpaceKind.Room) {
            info.channels.mapIndexed { index, channel ->
                Conversation(ids[index], kind, channel.title, Protection.Open, null, clock(), memberCount = info.members, parentId = rootId)
            }
        } else {
            emptyList()
        }
        store(root, children)
    }

    /** Puts [root] and its [children] in the list, keeping what the rows already had: previews, pins, drafts. */
    private fun store(root: Conversation, children: List<Conversation>) {
        world.conversations.update { list ->
            val known = list.associateBy { it.id }
            val kept = list.filterNot { it.id == root.id || it.parentId == root.id }
            val fresh = (listOf(root) + children).map { row ->
                known[row.id]?.copy(title = row.title, memberCount = row.memberCount, faces = row.faces, channels = row.channels) ?: row
            }
            (kept + fresh).sortedWith(DemoRepository.conversationOrder)
        }
        // Messages of a channel that was just removed go with it.
        val present = world.conversations.value.map { it.id }.toSet()
        world.messages.update { map -> map.filterKeys { id -> id in present || !id.startsWith(root.id + SEPARATOR) } }
    }

    private fun forget(rootId: String) {
        world.conversations.update { list -> list.filterNot { it.id == rootId || it.parentId == rootId } }
        world.messages.update { map -> map.filterKeys { it != rootId && !it.startsWith(rootId + SEPARATOR) } }
    }

    private fun post(conversationId: String, author: Contact, text: String) {
        val message = Message(
            id = "local-${UUID.randomUUID()}",
            conversationId = conversationId,
            authorId = author.id,
            text = text,
            sentAt = clock(),
            status = SendStatus.Read,
        )
        world.messages.update { map -> map + (conversationId to (map[conversationId].orEmpty() + message)) }
        val target = world.conversations.value.firstOrNull { it.id == conversationId }?.parentId ?: conversationId
        world.conversations.update { list ->
            list.map {
                if (it.id != target) return@map it
                it.copy(preview = Preview(author.name.substringBefore(' '), text), updatedAt = message.sentAt, unread = it.unread + 1)
            }.sortedWith(DemoRepository.conversationOrder)
        }
    }

    private fun shortId() = UUID.randomUUID().toString().take(SHORT_ID)

    companion object {
        const val SPACE_PREFIX = "space:"
        private const val SEPARATOR = ':'
        private const val MAX_TITLE = 64
        private const val FACES = 3
        private const val SHORT_ID = 8
        private const val INVITE_LENGTH = 16
        private const val GREETING_MILLIS = 1500L
    }
}

/** What the demo's groups say on their own: default channel names and the first hello. */
data class DemoCommunityTexts(
    val textChannel: String = "",
    val voiceChannel: String = "",
    val greeting: String = "",
)
