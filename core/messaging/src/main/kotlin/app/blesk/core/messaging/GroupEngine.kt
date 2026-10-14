package app.blesk.core.messaging

import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.GroupEntity
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Calls
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.EnvelopeType
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.GroupMember
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupRole
import app.blesk.shared.protocol.GroupState
import app.blesk.shared.protocol.TextSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/** What the group engine needs from the repository around it. */
internal interface GroupOutbox {
    suspend fun direct(recipient: String, content: Content)
    suspend fun sealed(recipient: String, content: Content, type: Int, body: String)
}

/**
 * Closed groups and rooms. Their state lives only on members' phones; admins change it and
 * send the new state to every member one-to-one. Messages are encrypted once with the
 * sender's SenderKey and fanned out. Leaving or removing someone moves the group to a new
 * epoch, and everyone hands out fresh sender keys to the remaining members only.
 */
internal class GroupEngine(
    private val db: BleskDatabase,
    private val crypto: GroupCrypto,
    private val me: String,
    private val outbox: GroupOutbox,
    private val clock: () -> Long,
) {
    private val random = SecureRandom()

    suspend fun state(groupId: String): GroupState? =
        db.groups().get(groupId)?.let { storageJson.decodeFromString(GroupState.serializer(), it.state) }

    /** The group a conversation belongs to: the group itself, or the room of a channel. */
    suspend fun groupOf(conversationId: String): GroupState? = state(conversationId.substringBefore(CHANNEL_SEPARATOR))

    suspend fun create(kind: GroupKind, title: String, members: List<String>): String {
        val id = UUID.randomUUID().toString()
        val channels = if (kind == GroupKind.Room) {
            listOf(GroupChannel(newId(), DEFAULT_TEXT_CHANNEL), GroupChannel(newId(), DEFAULT_VOICE_CHANNEL, GroupChannelKind.Voice))
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
            voiceKey = if (kind == GroupKind.Room) newVoiceKey() else null,
        )
        apply(state)
        broadcast(state, state.members.map { it.username })
        return id
    }

    // Admin actions; each returns false when the caller lacks the right.

    suspend fun rename(groupId: String, title: String) = change(groupId) { it.copy(title = title.trim().take(MAX_TITLE)) }

    suspend fun addMembers(groupId: String, usernames: List<String>) = change(groupId) { state ->
        val fresh = usernames.distinct().filter { name -> state.members.none { it.username == name } }
        state.copy(members = state.members + fresh.map { GroupMember(it, GroupRole.Member) })
    }

    suspend fun remove(groupId: String, username: String): Boolean {
        val before = state(groupId) ?: return false
        if (before.roleOf(username) == GroupRole.Owner) return false
        val changed = change(groupId) { state ->
            state.copy(
                members = state.members.filter { it.username != username },
                epoch = state.epoch + 1,
                voiceKey = state.voiceKey?.let { newVoiceKey() },
            )
        }
        // The removed person learns they are out, but gets nothing newer than that.
        if (changed) state(groupId)?.let { outbox.direct(username, update(it.copy(invite = null, voiceKey = null))) }
        return changed
    }

    suspend fun setRole(groupId: String, username: String, role: GroupRole): Boolean {
        val state = state(groupId) ?: return false
        if (role == GroupRole.Owner || state.roleOf(username) == GroupRole.Owner) return false
        if (state.roleOf(me) != GroupRole.Owner && role == GroupRole.Admin) return false
        return change(groupId) { s -> s.copy(members = s.members.map { if (it.username == username) it.copy(role = role) else it }) }
    }

    suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean) = change(groupId) { state ->
        if (state.kind != GroupKind.Room || state.channels.size >= MAX_CHANNELS) return@change state
        state.copy(channels = state.channels + GroupChannel(newId(), title.trim().take(MAX_TITLE), kind, adminsOnly))
    }

    suspend fun removeChannel(groupId: String, channelId: String) = change(groupId) { state ->
        val remaining = state.channels.filter { it.id != channelId }
        if (remaining.none { it.kind == GroupChannelKind.Text }) state else state.copy(channels = remaining)
    }

    /** A change checked by [GroupPolicy]; a ban tells the banned person they are out, as a removal does. */
    suspend fun change(groupId: String, change: GroupChange): Boolean {
        val state = state(groupId) ?: return false
        val next = GroupPolicy.apply(state, me, change, clock()) ?: return false
        val rotated = if (next.epoch != state.epoch) next.copy(voiceKey = next.voiceKey?.let { newVoiceKey() }) else next
        apply(rotated)
        broadcast(rotated, (state.members + rotated.members).map { it.username }.distinct())
        if (change is GroupChange.Ban) outbox.direct(change.username, update(rotated.copy(invite = null, voiceKey = null, invites = emptyList())))
        return true
    }

    /** Turns the invite link on (new code) or off. Returns the code. */
    suspend fun setInvite(groupId: String, enabled: Boolean): String? {
        var code: String? = null
        change(groupId) { state ->
            code = if (enabled) newCode() else null
            state.copy(invite = code)
        }
        return code
    }

    suspend fun leave(groupId: String) {
        val state = state(groupId) ?: return
        state.members.filter { it.username != me }.forEach {
            outbox.direct(it.username, Content(UUID.randomUUID().toString(), clock(), Body.GroupLeave(groupId)))
        }
        forget(groupId)
    }

    // Messages

    /** Encrypts once and queues the same ciphertext for every other member. */
    suspend fun send(conversationId: String, content: Content): Boolean {
        val state = groupOf(conversationId) ?: return false
        if (!canPost(state, conversationId, me)) return false
        shareSenderKey(state)
        val stamped = content.copy(group = conversationId)
        val body = withContext(Dispatchers.IO) { crypto.encrypt(state.id, state.epoch, stamped) }
        state.members.filter { it.username != me }.forEach { outbox.sealed(it.username, stamped, EnvelopeType.SENDERKEY, body) }
        return true
    }

    /** Decrypts a group envelope; null if it is not from a member of the group it names. */
    suspend fun open(sender: String, body: String): Content? {
        val content = withContext(Dispatchers.IO) { crypto.decrypt(sender, body) }
        val conversation = content.group ?: return null
        val state = groupOf(conversation) ?: return null
        return content.takeIf { state.roleOf(sender) != null }
    }

    fun canPost(state: GroupState, conversationId: String, username: String): Boolean {
        if (state.roleOf(username) == null) return false
        val channelId = conversationId.substringAfter(CHANNEL_SEPARATOR, "")
        val channel = state.channels.firstOrNull { it.id == channelId }
        return channel == null || !channel.adminsOnly || state.isAdmin(username)
    }

    // Incoming control messages

    suspend fun onUpdate(sender: String, incoming: GroupState) {
        val current = state(incoming.id)
        val trusted = if (current == null) incoming.isAdmin(sender) else current.isAdmin(sender) && incoming.version > current.version
        if (!trusted) return
        // Only the owner may hand the group over; an admin's state that moves ownership is not taken.
        val owner = incoming.members.firstOrNull { it.role == GroupRole.Owner }?.username
        if (current != null && owner != current.members.firstOrNull { it.role == GroupRole.Owner }?.username && current.roleOf(sender) != GroupRole.Owner) return
        if (incoming.roleOf(me) == null) {
            forget(incoming.id)
            return
        }
        // Titles come from another phone: cleaned here as if typed on this one.
        apply(
            incoming.copy(
                title = TextSafety.clean(incoming.title),
                description = TextSafety.clean(incoming.description, multiline = true).take(GroupPolicy.MAX_DESCRIPTION),
                channels = incoming.channels.map {
                    it.copy(title = TextSafety.clean(it.title), description = TextSafety.clean(it.description, multiline = true))
                },
                roles = incoming.roles.map { it.copy(title = TextSafety.clean(it.title)) },
            ),
        )
    }

    suspend fun onSenderKey(sender: String, key: Body.SenderKey) {
        val state = state(key.groupId) ?: return
        if (state.roleOf(sender) == null) return
        withContext(Dispatchers.IO) { crypto.accept(sender, key.distribution) }
    }

    suspend fun onJoinRequest(sender: String, request: Body.JoinRequest) {
        val state = state(request.groupId) ?: return
        if (!state.isAdmin(me) || state.roleOf(sender) != null || sender in state.banned) return
        val invite = state.invites.firstOrNull { it.code == request.code }
        when {
            invite != null && invite.usable(clock()) -> change(request.groupId) { s ->
                s.copy(
                    members = s.members + GroupMember(sender, GroupRole.Member),
                    invites = s.invites.map { if (it.code == invite.code) it.copy(joined = it.joined + sender) else it },
                )
            }
            invite == null && state.invite != null && request.code == state.invite -> addMembers(request.groupId, listOf(sender))
        }
    }

    suspend fun onLeave(sender: String, groupId: String) {
        val state = state(groupId) ?: return
        if (state.roleOf(sender) == null) return
        val left = state.copy(members = state.members.filter { it.username != sender }, epoch = state.epoch + 1)
        apply(left)
        // One admin, the first in the list, replaces the voice key so the one who left cannot listen in.
        if (left.voiceKey != null && left.members.firstOrNull { left.isAdmin(it.username) }?.username == me) {
            change(groupId) { it.copy(voiceKey = newVoiceKey()) }
        }
    }

    /** The media key for the room's voice channels; an admin creates one for rooms made before calls existed. */
    suspend fun voiceKey(groupId: String): String? {
        val state = state(groupId) ?: return null
        if (state.kind != GroupKind.Room) return null
        state.voiceKey?.let { return it }
        if (!change(groupId) { it.copy(voiceKey = newVoiceKey()) }) return null
        return state(groupId)?.voiceKey
    }

    private fun newVoiceKey(): String =
        Base64.getEncoder().encodeToString(ByteArray(Calls.KEY_BYTES).also(random::nextBytes))

    // Internals

    private suspend fun change(groupId: String, transform: (GroupState) -> GroupState): Boolean {
        val state = state(groupId) ?: return false
        if (!state.isAdmin(me)) return false
        val next = transform(state)
        if (next == state) return true
        val versioned = next.copy(version = state.version + 1)
        apply(versioned)
        broadcast(versioned, (state.members + versioned.members).map { it.username }.distinct())
        return true
    }

    private suspend fun broadcast(state: GroupState, recipients: List<String>) {
        recipients.filter { it != me && state.roleOf(it) != null }.forEach { outbox.direct(it, update(state)) }
    }

    private fun update(state: GroupState) = Content(UUID.randomUUID().toString(), clock(), Body.GroupUpdate(state))

    /** Gives our sender key for the current epoch to every member who does not have it yet. */
    private suspend fun shareSenderKey(state: GroupState) {
        val entity = db.groups().get(state.id) ?: return
        val holders = if (entity.keyEpoch == state.epoch) decodeNames(entity.keyHolders) else emptyList()
        val missing = state.members.map { it.username }.filter { it != me && it !in holders }
        if (missing.isEmpty()) return
        val distribution = withContext(Dispatchers.IO) { crypto.distribution(state.id, state.epoch) }
        missing.forEach { member ->
            outbox.direct(member, Content(UUID.randomUUID().toString(), clock(), Body.SenderKey(state.id, state.epoch, distribution)))
        }
        db.groups().upsert(entity.copy(keyHolders = encodeNames(holders + missing), keyEpoch = state.epoch))
    }

    /** Stores the state and keeps the conversation rows (the group, or the room and its text channels) in step. */
    private suspend fun apply(state: GroupState) {
        val existing = db.groups().get(state.id)
        db.groups().upsert(
            existing?.copy(state = storageJson.encodeToString(GroupState.serializer(), state))
                ?: GroupEntity(state.id, storageJson.encodeToString(GroupState.serializer(), state)),
        )
        val kind = if (state.kind == GroupKind.Room) ConversationKind.Room else ConversationKind.Group
        val now = clock()
        val root = db.conversations().get(state.id)
        db.conversations().upsert(
            (root ?: ConversationEntity(state.id, kind.name, state.title, Protection.EndToEnd.name, null, now, previewText = ""))
                .copy(title = state.title),
        )
        if (state.kind != GroupKind.Room) return
        val textIds = state.channels.filter { it.kind == GroupChannelKind.Text }.map { channelConversation(state.id, it.id) }
        db.conversations().children(state.id).filter { it.id !in textIds }.forEach { db.conversations().deleteWithChildren(it.id) }
        state.channels.filter { it.kind == GroupChannelKind.Text }.forEach { channel ->
            val id = channelConversation(state.id, channel.id)
            val row = db.conversations().get(id)
            val fresh = ConversationEntity(
                id, ConversationKind.Room.name, channel.title, Protection.EndToEnd.name, null, now, parentId = state.id,
            )
            db.conversations().upsert((row ?: fresh).copy(title = channel.title))
        }
    }

    private suspend fun forget(groupId: String) {
        db.conversations().children(groupId).forEach { db.messages().deleteConversation(it.id) }
        db.messages().deleteConversation(groupId)
        db.conversations().deleteWithChildren(groupId)
        db.groups().delete(groupId)
    }

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun newCode(): String = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(INVITE_BYTES).also(random::nextBytes))

    private fun decodeNames(raw: String): List<String> =
        runCatching { storageJson.decodeFromString(ListSerializer(String.serializer()), raw) }.getOrDefault(emptyList())

    private fun encodeNames(names: List<String>) = storageJson.encodeToString(ListSerializer(String.serializer()), names)

    companion object {
        const val CHANNEL_SEPARATOR = ':'
        private const val MAX_TITLE = 64
        private const val MAX_CHANNELS = 20
        private const val INVITE_BYTES = 12
        const val DEFAULT_TEXT_CHANNEL = "общее"
        const val DEFAULT_VOICE_CHANNEL = "голосовой"

        fun channelConversation(groupId: String, channelId: String) = "$groupId$CHANNEL_SEPARATOR$channelId"
    }
}
