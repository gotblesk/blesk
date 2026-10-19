package app.blesk.core.messaging

import androidx.room.withTransaction
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.GroupEntity
import app.blesk.core.data.db.LeftGroupEntity
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
import app.blesk.shared.protocol.Right
import app.blesk.shared.protocol.TextSafety
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Where the group engine queues what it sends. Queueing writes the row and nothing more, so it can be part of a
 * change's transaction, and a new state and the updates telling members of it are stored together or not at all.
 *
 * Sending starts with [flush], once that transaction is committed. A pass started inside it reads the queue on a
 * connection of its own, and with the write-ahead log the phone's database keeps, that connection does not see rows
 * not yet committed: the pass would find nothing and end, and the updates would wait for whatever asks next.
 */
internal interface GroupOutbox {
    /** Content to encrypt one-to-one when it goes out. */
    suspend fun queue(recipient: String, content: Content)

    /** A group message, encrypted once with our sender key and sent to each member as it is. */
    suspend fun queueSealed(recipient: String, content: Content, type: Int, body: String)

    /** Sends what waits, in a pass of the outbox's own that the caller need not wait for. */
    fun flush(): Job
}

/**
 * Closed groups and rooms. Their state lives only on members' phones; admins change it and
 * send the new state to every member one-to-one. Messages are encrypted once with the
 * sender's SenderKey and fanned out. Leaving or removing someone moves the group to a new
 * epoch, and everyone hands out fresh sender keys to the remaining members only.
 *
 * Every change reads the state, works out the next one and writes it in one transaction, so another change, an
 * admin's update or someone leaving lands before or after it, never between its read and its write. What it queues
 * for members is sent once the transaction is committed.
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
        queueing {
            store(state, new = true)
            broadcast(state, state.members.map { it.username })
        }
        return id
    }

    // Admin actions; each returns false when the caller lacks the right.

    suspend fun rename(groupId: String, title: String) = change(groupId) { it.copy(title = title.trim().take(MAX_TITLE)) }

    suspend fun addMembers(groupId: String, usernames: List<String>) = change(groupId) { state ->
        val fresh = usernames.distinct().filter { name -> state.members.none { it.username == name } }
        state.copy(members = state.members + fresh.map { GroupMember(it, GroupRole.Member) })
    }

    suspend fun remove(groupId: String, username: String) = change(groupId, GroupChange.Remove(username))

    suspend fun setRole(groupId: String, username: String, role: GroupRole) = change(groupId) { state ->
        val refused = role == GroupRole.Owner || state.roleOf(username) == GroupRole.Owner ||
            (state.roleOf(me) != GroupRole.Owner && role == GroupRole.Admin)
        if (refused) null else state.copy(members = state.members.map { if (it.username == username) it.copy(role = role) else it })
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
    suspend fun change(groupId: String, change: GroupChange): Boolean = queueing {
        val state = state(groupId) ?: return@queueing false
        val next = GroupPolicy.apply(state, me, change, clock()) ?: return@queueing false
        val rotated = if (next.epoch != state.epoch) next.copy(voiceKey = next.voiceKey?.let { newVoiceKey() }) else next
        store(rotated)
        broadcast(rotated, (state.members + rotated.members).map { it.username }.distinct())
        val gone = rotated.copy(invite = null, voiceKey = null, invites = emptyList())
        when (change) {
            is GroupChange.Ban -> outbox.queue(change.username, update(gone))
            is GroupChange.Remove -> outbox.queue(change.username, update(gone))
            GroupChange.Dissolve -> {
                state.members.filter { it.username != me }.forEach { outbox.queue(it.username, update(gone)) }
                forget(groupId, rotated.version, byUs = true)
            }
            else -> Unit
        }
        true
    }

    /** Turns the invite link on (new code) or off. Returns the code. */
    suspend fun setInvite(groupId: String, enabled: Boolean): String? {
        val code = if (enabled) newCode() else null
        return if (change(groupId) { it.copy(invite = code) }) code else null
    }

    suspend fun leave(groupId: String) = queueing {
        val state = state(groupId) ?: return@queueing
        state.members.filter { it.username != me }.forEach {
            outbox.queue(it.username, Content(UUID.randomUUID().toString(), clock(), Body.GroupLeave(groupId)))
        }
        forget(groupId, state.version, byUs = true)
    }

    /** We ask to join by an invite: the admin's answer may bring the group back, even one we once left. */
    suspend fun joining(groupId: String) = db.groups().unmarkLeft(groupId)

    // Messages

    /** Encrypts once, queues the same ciphertext for every other member and sends it. */
    suspend fun send(conversationId: String, content: Content): Boolean {
        val state = groupOf(conversationId) ?: return false
        if (!canPost(state, conversationId, me)) return false
        shareSenderKey(state)
        val stamped = content.copy(group = conversationId)
        val body = withContext(Dispatchers.IO) { crypto.encrypt(state.id, state.epoch, stamped) }
        state.members.filter { it.username != me }.forEach { outbox.queueSealed(it.username, stamped, EnvelopeType.SENDERKEY, body) }
        outbox.flush()
        return true
    }

    /** Decrypts a group envelope; null if it is not from a member of the group it names. */
    suspend fun open(sender: String, body: String): Content? {
        val content = withContext(Dispatchers.IO) { crypto.decrypt(sender, body) }
        val conversation = content.group ?: return null
        val state = groupOf(conversation) ?: return null
        return content.takeIf { state.roleOf(sender) != null }
    }

    fun canPost(state: GroupState, conversationId: String, username: String): Boolean =
        state.roleOf(username) != null && GroupPolicy.canWrite(state, username, conversationId)

    // Incoming control messages

    suspend fun onUpdate(sender: String, incoming: GroupState) = db.withTransaction {
        val current = state(incoming.id)
        val trusted = if (current == null) {
            incoming.isAdmin(sender) && mayComeBack(incoming)
        } else {
            GroupPolicy.manages(current, sender) && incoming.version > current.version
        }
        if (!trusted) return@withTransaction
        // Only the owner may hand the group over; an admin's state that moves ownership is not taken.
        val owner = incoming.members.firstOrNull { it.role == GroupRole.Owner }?.username
        val previous = current?.members?.firstOrNull { it.role == GroupRole.Owner }?.username
        if (current != null && owner != previous && current.roleOf(sender) != GroupRole.Owner) return@withTransaction
        if (incoming.roleOf(me) == null) {
            if (current != null) forget(incoming.id, incoming.version, byUs = false)
            return@withTransaction
        }
        store(cleaned(incoming), new = current == null)
        if (current == null) db.groups().unmarkLeft(incoming.id)
    }

    /** Titles come from another phone: cleaned here as if typed on this one. */
    private fun cleaned(incoming: GroupState) = incoming.copy(
        title = TextSafety.clean(incoming.title),
        description = TextSafety.clean(incoming.description, multiline = true).take(GroupPolicy.MAX_DESCRIPTION),
        channels = incoming.channels.map {
            it.copy(title = TextSafety.clean(it.title), description = TextSafety.clean(it.description, multiline = true))
        },
        roles = incoming.roles.map { it.copy(title = TextSafety.clean(it.title)) },
    )

    /**
     * Whether a group this phone let go of may return. An admin may have sent a state before he learned we were out:
     * removed or dissolved, only a newer state than the one that put us out counts; left by us, none does, as we
     * come back only by asking to join ourselves. The admin drops us on his side without a word, so a group brought
     * back by such a late state would stay here for good, silent.
     */
    private suspend fun mayComeBack(incoming: GroupState): Boolean {
        val left = db.groups().leftGroup(incoming.id) ?: return true
        return !left.byUs && incoming.version > left.version
    }

    suspend fun onSenderKey(sender: String, key: Body.SenderKey) {
        val state = state(key.groupId) ?: return
        if (state.roleOf(sender) == null) return
        withContext(Dispatchers.IO) { crypto.accept(sender, key.distribution) }
    }

    suspend fun onJoinRequest(sender: String, request: Body.JoinRequest) {
        change(request.groupId) { state ->
            val invite = state.invites.firstOrNull { it.code == request.code }
            val joined = state.copy(members = state.members + GroupMember(sender, GroupRole.Member))
            when {
                !GroupPolicy.can(state, me, Right.Invite) || state.roleOf(sender) != null || sender in state.banned -> null
                invite != null && invite.usable(clock()) ->
                    joined.copy(invites = state.invites.map { if (it.code == invite.code) it.copy(joined = it.joined + sender) else it })
                invite == null && state.invite != null && request.code == state.invite -> joined
                else -> null
            }
        }
    }

    suspend fun onLeave(sender: String, groupId: String) {
        val left = db.withTransaction {
            val state = state(groupId)?.takeIf { it.roleOf(sender) != null } ?: return@withTransaction null
            state.copy(members = state.members.filter { it.username != sender }, epoch = state.epoch + 1).also { store(it) }
        } ?: return
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
        // Another change may have made one meanwhile; that one stays.
        if (!change(groupId) { if (it.voiceKey != null) it else it.copy(voiceKey = newVoiceKey()) }) return null
        return state(groupId)?.voiceKey
    }

    private fun newVoiceKey(): String =
        Base64.getEncoder().encodeToString(ByteArray(Calls.KEY_BYTES).also(random::nextBytes))

    // Internals

    /** [transform] returns null to refuse, which returns false; returning the state unchanged is no change. */
    private suspend fun change(groupId: String, transform: (GroupState) -> GroupState?): Boolean = queueing {
        val state = state(groupId) ?: return@queueing false
        if (!GroupPolicy.manages(state, me)) return@queueing false
        val next = transform(state) ?: return@queueing false
        if (next != state) {
            val versioned = next.copy(version = state.version + 1)
            store(versioned)
            broadcast(versioned, (state.members + versioned.members).map { it.username }.distinct())
        }
        true
    }

    /**
     * Runs [block], which queues updates, in one transaction, and sends them once it is committed. Sends in a finally:
     * a caller cancelled once the commit is done, as it hands back the result, a screen closed during the save, would
     * leave the updates to whatever asks next.
     */
    private suspend fun <T> queueing(block: suspend () -> T): T = try {
        db.withTransaction(block)
    } finally {
        outbox.flush()
    }

    private suspend fun broadcast(state: GroupState, recipients: List<String>) {
        recipients.filter { it != me && state.roleOf(it) != null }.forEach { outbox.queue(it, update(state)) }
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
            outbox.queue(member, Content(UUID.randomUUID().toString(), clock(), Body.SenderKey(state.id, state.epoch, distribution)))
        }
        // Making the key and queueing its copies is not one transaction with the read above: by now the state may have
        // moved on or the group may be gone, so only the holders are written, merged with those another send recorded.
        db.groups().changeKeyHolders(state.id, state.epoch) { current ->
            encodeNames((current?.let(::decodeNames).orEmpty() + missing).distinct())
        }
    }

    /**
     * Stores the state and keeps the conversation rows (the group, or the room and its text channels) in step. Runs
     * inside the transaction that read the group, so it writes a row that is there, or creates a [new] one.
     */
    private suspend fun store(state: GroupState, new: Boolean = false) {
        val json = storageJson.encodeToString(GroupState.serializer(), state)
        if (new) db.groups().insertIfAbsent(GroupEntity(state.id, json)) else db.groups().setState(state.id, json)
        keepConversations(state)
    }

    private suspend fun keepConversations(state: GroupState) {
        val kind = if (state.kind == GroupKind.Room) ConversationKind.Room else ConversationKind.Group
        val now = clock()
        db.conversations().insertIfAbsent(
            ConversationEntity(state.id, kind.name, state.title, Protection.EndToEnd.name, null, now, previewText = ""),
        )
        db.conversations().setTitle(state.id, state.title)
        if (state.kind != GroupKind.Room) return
        val visible = state.channels.filter { it.kind == GroupChannelKind.Text && GroupPolicy.sees(state, me, it) }
        val textIds = visible.map { channelConversation(state.id, it.id) }
        db.conversations().children(state.id).filter { it.id !in textIds }.forEach { db.conversations().deleteWithChildren(it.id) }
        visible.forEach { channel ->
            val id = channelConversation(state.id, channel.id)
            db.conversations().insertIfAbsent(
                ConversationEntity(id, ConversationKind.Room.name, channel.title, Protection.EndToEnd.name, null, now, parentId = state.id),
            )
            db.conversations().setTitle(id, channel.title)
        }
    }

    /** Deletes the group and its chats, and notes it was let go of at [version], left or dissolved [byUs] or not. */
    private suspend fun forget(groupId: String, version: Int, byUs: Boolean) = db.withTransaction {
        db.conversations().children(groupId).forEach { db.messages().deleteConversation(it.id) }
        db.messages().deleteConversation(groupId)
        db.conversations().deleteWithChildren(groupId)
        db.groups().delete(groupId)
        db.groups().markLeft(LeftGroupEntity(groupId, version, byUs))
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
