package app.blesk.core.messaging

import androidx.room.withTransaction
import app.blesk.core.crypto.signal.GroupCrypto
import app.blesk.core.data.calls.VoiceAccess
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.GroupEntity
import app.blesk.core.data.db.GroupSenderKeyEntity
import app.blesk.core.data.db.HeldGroupMessageEntity
import app.blesk.core.data.db.LeftGroupEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.model.Attachment
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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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

/** What becomes of a group message that arrives: kept, held for the state it was written in, or dropped. */
internal enum class Admission { Store, Hold, Drop }

/** What became of a member's state of a group: taken, ignored as not newer or not theirs to send, or refused. */
internal enum class UpdateOutcome { Taken, Ignored, Refused }

/** A held message whose state has arrived: what [sender] sent, to be judged again. */
internal data class Released(val sender: String, val content: Content)

/**
 * Closed groups and rooms. Their state lives only on members' phones; admins change it and
 * send the new state to every member one-to-one. A message is encrypted once and goes only to
 * those who may read it: everyone in the group under one of the sender's keys, the readers of a
 * closed channel under a key of that channel. A key is not used again once someone who got it may
 * no longer read, or once someone leaves or is removed and the group moves to a new epoch. A closed
 * voice channel has a media key and a LiveKit room of its own, kept the same way by [VoiceKeys].
 *
 * A phone keeps only what its own state lets it read, whatever reaches it: a message from a sender whose state is
 * newer waits for that state, and losing a channel takes its messages and files off the phone.
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
    /** The chat list's line for a message that is only a file. */
    private val labels: (Attachment?) -> String,
    /** Messages with files, taken off the phone with their channel or group: their downloads and files go next. */
    private val purged: suspend (List<MessageEntity>) -> Unit,
    /** The keys of closed voice channels, ours to hand out where this phone keeps them. */
    private val voiceKeys: VoiceKeys,
) {
    private val random = SecureRandom()

    /** One send at a time: two at once would each find no key for an audience and hand out one of their own. */
    private val sending = Mutex()

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
            voiceKey = if (kind == GroupKind.Room) voiceKeys.newMediaKey() else null,
        )
        queueing(id) {
            store(state, new = true)
            broadcast(state, state.members.map { it.username })
        }
        return id
    }

    // Admin actions; each returns false when the caller lacks the right, the one every other phone asks of it.

    suspend fun rename(groupId: String, title: String) = change(groupId) { state ->
        if (!GroupPolicy.can(state, me, Right.EditInfo)) null else state.copy(title = title.trim().take(MAX_TITLE))
    }

    suspend fun addMembers(groupId: String, usernames: List<String>) = change(groupId) { state ->
        if (!GroupPolicy.can(state, me, Right.Invite)) return@change null
        val fresh = usernames.distinct().filter { name -> state.roleOf(name) == null && name !in state.banned }
        state.copy(members = state.members + fresh.map { GroupMember(it, GroupRole.Member) })
    }

    suspend fun remove(groupId: String, username: String) = change(groupId, GroupChange.Remove(username))

    /** Makes [username] an admin with every admin right, or a member again; the group is handed over only by a transfer. */
    suspend fun setRole(groupId: String, username: String, role: GroupRole) = when (role) {
        GroupRole.Owner -> false
        GroupRole.Admin -> change(groupId, GroupChange.Admin(username, Right.ADMIN_ALL))
        GroupRole.Member -> change(groupId, GroupChange.Admin(username, null))
    }

    suspend fun addChannel(groupId: String, title: String, kind: GroupChannelKind, adminsOnly: Boolean) = change(groupId) { state ->
        when {
            !GroupPolicy.can(state, me, Right.Channels) -> null
            state.kind != GroupKind.Room || state.channels.size >= MAX_CHANNELS -> state
            else -> state.copy(channels = state.channels + GroupChannel(newId(), title.trim().take(MAX_TITLE), kind, adminsOnly))
        }
    }

    /** Only a channel we see is ours to delete, as [GroupPolicy.admitsVisibility] decides for every phone. */
    suspend fun removeChannel(groupId: String, channelId: String) = change(groupId) { state ->
        val channel = state.channels.firstOrNull { it.id == channelId }
        val remaining = state.channels.filter { it.id != channelId }
        when {
            !GroupPolicy.can(state, me, Right.Channels) || channel?.let { GroupPolicy.sees(state, me, it) } == false -> null
            remaining.none { it.kind == GroupChannelKind.Text } -> state
            else -> state.copy(channels = remaining)
        }
    }

    /** A change checked by [GroupPolicy]; a ban tells the banned person they are out, as a removal does. */
    suspend fun change(groupId: String, change: GroupChange): Boolean = queueing(groupId) {
        val state = state(groupId) ?: return@queueing false
        val next = GroupPolicy.apply(state, me, change, clock()) ?: return@queueing false
        val rotated = if (next.epoch != state.epoch) next.copy(voiceKey = next.voiceKey?.let { voiceKeys.newMediaKey() }) else next
        store(rotated)
        broadcast(rotated, (state.members + rotated.members).map { it.username }.distinct())
        voiceKeys.keep(rotated)
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
        val changed = change(groupId) { state -> if (!GroupPolicy.can(state, me, Right.Invite)) null else state.copy(invite = code) }
        return if (changed) code else null
    }

    suspend fun leave(groupId: String) = queueing(groupId) {
        val state = state(groupId) ?: return@queueing
        state.members.filter { it.username != me }.forEach {
            outbox.queue(it.username, Content(UUID.randomUUID().toString(), clock(), Body.GroupLeave(groupId)))
        }
        forget(groupId, state.version, byUs = true)
    }

    /** We ask to join by an invite: the admin's answer may bring the group back, even one we once left. */
    suspend fun joining(groupId: String) = db.groups().unmarkLeft(groupId)

    // Messages

    /** Encrypts once, queues the same ciphertext for everyone else who may read [conversationId] and sends it. */
    suspend fun send(conversationId: String, content: Content): Boolean = sending.withLock {
        val state = groupOf(conversationId) ?: return@withLock false
        if (!canPost(state, conversationId, me)) return@withLock false
        val readers = GroupPolicy.readers(state, conversationId) - me
        // A channel only we read, or a group of one: there is no one to hand a key to, so nothing to encrypt with.
        if (readers.isEmpty()) return@withLock true
        val stamped = content.copy(group = conversationId, groupVersion = state.version)
        try {
            val key = senderKey(state, GroupPolicy.keyScope(state, conversationId), readers)
            val body = withContext(Dispatchers.IO) { crypto.encrypt(key, stamped) }
            readers.forEach { outbox.queueSealed(it, stamped, EnvelopeType.SENDERKEY, body) }
        } finally {
            outbox.flush()
        }
        true
    }

    /** Decrypts a group envelope; null if it is not from a member of the group it names. */
    suspend fun open(sender: String, body: String): Content? {
        val content = withContext(Dispatchers.IO) { crypto.decrypt(sender, body) }
        val conversation = content.group ?: return null
        val state = groupOf(conversation) ?: return null
        return content.takeIf { state.roleOf(sender) != null }
    }

    private fun canPost(state: GroupState, conversationId: String, username: String): Boolean =
        state.roleOf(username) != null && GroupPolicy.canWrite(state, username, conversationId)

    /**
     * What this phone does with [content], from [sender] in [conversationId], by its own [state]: keeps it only in a
     * chat we have, the group or a text channel we see, if [sender] may read there too and, for what only a writer
     * sends, write there. Otherwise it waits if [sender] wrote it in a newer state than ours, one that may let us read
     * it, and is dropped if not.
     */
    fun admission(state: GroupState, conversationId: String, sender: String, content: Content): Admission {
        val chat = conversationId == state.id || GroupPolicy.channelOf(state, conversationId)?.kind == GroupChannelKind.Text
        val readable = chat && GroupPolicy.reads(state, me, conversationId) && GroupPolicy.reads(state, sender, conversationId)
        val written = (content.body !is Body.Text && content.body !is Body.Timer) || canPost(state, conversationId, sender)
        return when {
            readable && written -> Admission.Store
            content.groupVersion > state.version -> Admission.Hold
            else -> Admission.Drop
        }
    }

    /** Keeps [content] out of sight until the state it was written in arrives; [released] gives it back then. */
    suspend fun hold(sender: String, content: Content) {
        val conversation = content.group ?: return
        val held = HeldGroupMessageEntity(
            groupId = conversation.substringBefore(CHANNEL_SEPARATOR),
            sender = sender,
            content = storageJson.encodeToString(Content.serializer(), content),
            version = content.groupVersion,
            heldAt = clock(),
        )
        db.groups().hold(held)
    }

    /**
     * The held messages of [groupId] its state has caught up with, in the order they came, taken off the list. A
     * refused state kept with them, by now no newer than ours, goes with them and is not given back.
     */
    suspend fun released(groupId: String): List<Released> {
        val state = state(groupId) ?: return emptyList()
        return db.groups().releasable(groupId, state.version).mapNotNull { row ->
            heldContent(row)?.takeIf { it.body !is Body.GroupUpdate }?.let { Released(row.sender, it) }
        }
    }

    private fun heldContent(row: HeldGroupMessageEntity): Content? =
        runCatching { storageJson.decodeFromString(Content.serializer(), row.content) }.getOrNull()

    /**
     * Run once on start: takes off the phone what it may not keep, from channels lost while it was off or before it
     * checked, lets go of messages held too long, hands out the voice keys we keep that someone still lacks, and gives
     * back the held messages whose state is here.
     */
    suspend fun tidy(): List<Released> {
        db.groups().dropHeldBefore(clock() - HOLD_MILLIS)
        val present = db.groups().ids()
        (present + db.groups().leftIds()).distinct().forEach { purge(it) }
        present.forEach { renewVoiceKeys(it) }
        return present.flatMap { released(it) }
    }

    // Incoming control messages

    /**
     * A state of a group from [sender]; once it is taken, what it no longer lets us read leaves the phone, and the
     * voice keys it calls for, if we keep them, go out. One refused may be built on a state yet to reach us, an admin's
     * on the owner's: it is kept, and tried again once another is taken or someone leaves.
     */
    suspend fun onUpdate(sender: String, incoming: GroupState): UpdateOutcome = queueing(incoming.id) {
        take(sender, incoming).also { outcome ->
            when (outcome) {
                UpdateOutcome.Taken -> retryRefused(incoming.id)
                UpdateOutcome.Refused -> keepRefused(sender, incoming)
                UpdateOutcome.Ignored -> Unit
            }
        }
    }

    private suspend fun take(sender: String, incoming: GroupState): UpdateOutcome {
        val current = state(incoming.id)
        val trusted = if (current == null) {
            isGroupId(incoming.id) && incoming.isAdmin(sender) && mayComeBack(incoming)
        } else {
            GroupPolicy.manages(current, sender) && incoming.version > current.version
        }
        if (!trusted) return UpdateOutcome.Ignored
        // Taken only as a state the sender could have made from ours, or a modified app would give itself rights and
        // channels in one step. Refused whole: mended field by field, it would become a state no phone made. This
        // phone stays behind until a state it can take arrives, and messages written in the newer one wait for that.
        if (current != null && !admits(current, incoming, sender)) return UpdateOutcome.Refused
        when {
            incoming.roleOf(me) != null -> {
                val taken = cleaned(incoming)
                store(taken, new = current == null)
                voiceKeys.keep(taken)
                if (current == null) db.groups().unmarkLeft(incoming.id)
            }
            current != null -> forget(incoming.id, incoming.version, byUs = false)
            else -> return UpdateOutcome.Ignored
        }
        return UpdateOutcome.Taken
    }

    /**
     * Whether [sender] could have made [incoming] from [current], as [GroupPolicy.admits] decides, both cleaned as this
     * phone keeps them: a title another phone cleaned is no change of it. A state without us need only put us out as
     * [GroupPolicy.removes] lets [sender]: it is not kept, and the one sent to whoever is removed leaves out the
     * invites and the voice key, no longer theirs to see.
     */
    private fun admits(current: GroupState, incoming: GroupState, sender: String): Boolean = if (incoming.roleOf(me) == null) {
        GroupPolicy.removes(current, sender, me)
    } else {
        GroupPolicy.admits(cleaned(current), cleaned(incoming), sender)
    }

    /** Keeps a refused state with the held messages, waiting as they do, for as long as they do. */
    private suspend fun keepRefused(sender: String, incoming: GroupState) {
        val now = clock()
        val update = Content(UUID.randomUUID().toString(), now, Body.GroupUpdate(incoming))
        db.groups().hold(
            HeldGroupMessageEntity(
                groupId = incoming.id,
                sender = sender,
                content = storageJson.encodeToString(Content.serializer(), update),
                version = incoming.version,
                heldAt = now,
            ),
        )
    }

    /**
     * Tries the kept states newer than ours again, oldest first, each against the state the one before left: those
     * taken and those no longer newer go, those still refused wait on. Stops once the group is gone from the phone.
     */
    private suspend fun retryRefused(groupId: String) {
        val version = state(groupId)?.version ?: return
        for (row in db.groups().heldAfter(groupId, version)) {
            val update = heldContent(row)?.body as? Body.GroupUpdate ?: continue
            if (state(groupId) == null) return
            if (take(row.sender, update.state) != UpdateOutcome.Refused) db.groups().unhold(row.id)
        }
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

    /** [sender] left [groupId]; a state kept as refused while they were still here may now be taken. */
    suspend fun onLeave(sender: String, groupId: String) {
        val left = queueing(groupId) {
            val state = state(groupId)?.takeIf { it.roleOf(sender) != null } ?: return@queueing null
            val next = state.copy(members = state.members.filter { it.username != sender }, epoch = state.epoch + 1)
            store(next)
            voiceKeys.keep(next)
            retryRefused(groupId)
            state(groupId)
        } ?: return
        // The keeper replaces the key of the voice channels open to all, so the one who left cannot listen in.
        if (left.voiceKey != null && voiceKeys.keeps(left)) change(groupId) { it.copy(voiceKey = voiceKeys.newMediaKey()) }
    }

    // Voice channels

    /**
     * Where we talk in a room's voice channel, and with which key: the room's own for a channel open to all, the
     * channel's own for a closed one. Null for a channel we do not see, and for a closed one whose key has not reached
     * us yet.
     */
    suspend fun voiceAccess(groupId: String, channelId: String): VoiceAccess? {
        val state = state(groupId)?.takeIf { it.kind == GroupKind.Room } ?: return null
        val channel = state.channels.firstOrNull { it.id == channelId && it.kind == GroupChannelKind.Voice } ?: return null
        if (!GroupPolicy.sees(state, me, channel)) return null
        if (!VoiceKeys.hasOwnKey(channel)) return openVoiceKey(state)?.let { VoiceAccess(Calls.voiceRoom(groupId, channelId), it) }
        // The keeper looks over its keys first: a channel closed before they existed gets one now.
        if (voiceKeys.keeps(state)) renewVoiceKeys(groupId)
        return voiceKeys.stored(state, channelId)
    }

    /** The key of the room's voice channels open to all; an admin makes one for a room made before calls existed. */
    private suspend fun openVoiceKey(state: GroupState): String? {
        state.voiceKey?.let { return it }
        // Another change may have made one meanwhile; that one stays.
        if (!change(state.id) { if (it.voiceKey != null) it else it.copy(voiceKey = voiceKeys.newMediaKey()) }) return null
        return state(state.id)?.voiceKey
    }

    /** [VoiceKeys.keep] for the state on the phone, outside any change of it. */
    private suspend fun renewVoiceKeys(groupId: String) {
        try {
            db.withTransaction { state(groupId)?.let { voiceKeys.keep(it) } }
        } finally {
            outbox.flush()
        }
    }

    // Internals

    /** [transform] returns null to refuse, which returns false; returning the state unchanged is no change. */
    private suspend fun change(groupId: String, transform: (GroupState) -> GroupState?): Boolean = queueing(groupId) {
        val state = state(groupId) ?: return@queueing false
        if (!GroupPolicy.manages(state, me)) return@queueing false
        val next = transform(state) ?: return@queueing false
        if (next != state) {
            val versioned = next.copy(version = state.version + 1)
            store(versioned)
            broadcast(versioned, (state.members + versioned.members).map { it.username }.distinct())
            voiceKeys.keep(versioned)
        }
        true
    }

    /**
     * Runs [block], a change of [groupId] that queues updates, in one transaction, then sends them and takes off the
     * phone what the stored state no longer lets us read. Both in a finally: a caller cancelled once the commit is
     * done, as it hands back the result, a screen closed during the save, would leave them to whatever comes next.
     * Whether it committed is not known there, and need not be: either way the state on the phone is what counts.
     */
    private suspend fun <T> queueing(groupId: String, block: suspend () -> T): T = try {
        db.withTransaction(block)
    } finally {
        outbox.flush()
        withContext(NonCancellable) { purge(groupId) }
    }

    private suspend fun broadcast(state: GroupState, recipients: List<String>) {
        recipients.filter { it != me && state.roleOf(it) != null }.forEach { outbox.queue(it, update(state)) }
    }

    private fun update(state: GroupState) = Content(UUID.randomUUID().toString(), clock(), Body.GroupUpdate(state))

    /**
     * Our sender key for [scope], handed to each of [readers] who does not have it yet. A key stays ours only while
     * everyone who got it is still among [readers] and the epoch is the same; otherwise a new one with a random id
     * takes its place, so a key is never used again after someone lost the right to read, nor after an epoch went
     * back. Runs under [sending], so its row is read and written by one send at a time.
     */
    private suspend fun senderKey(state: GroupState, scope: String, readers: List<String>): UUID {
        val row = db.groups().senderKey(state.id, scope)
        val holders = row?.takeIf { it.epoch == state.epoch }?.let { decodeNames(it.holders) }?.takeIf { readers.containsAll(it) }
        val id = if (row != null && holders != null) UUID.fromString(row.distributionId) else UUID.randomUUID()
        val missing = readers - holders.orEmpty().toSet()
        if (missing.isEmpty()) return id
        val distribution = withContext(Dispatchers.IO) { crypto.distribution(id) }
        val handedOut = encodeNames(holders.orEmpty() + missing)
        // Queued and noted together: a send cut short between the two would leave the key on its way to someone not on
        // the list, and it would stay in use after they lost the right to read.
        db.withTransaction {
            missing.forEach { reader ->
                outbox.queue(reader, Content(UUID.randomUUID().toString(), clock(), Body.SenderKey(state.id, state.epoch, distribution)))
            }
            db.groups().putSenderKey(GroupSenderKeyEntity(state.id, scope, id.toString(), state.epoch, handedOut))
        }
        return id
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
        val visible = channelsWeRead(state)
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

    /** The text channels of a room [state] lets us see; a group has none. */
    private fun channelsWeRead(state: GroupState): List<GroupChannel> =
        if (state.kind != GroupKind.Room) {
            emptyList()
        } else {
            state.channels.filter { it.kind == GroupChannelKind.Text && GroupPolicy.sees(state, me, it) }
        }

    /**
     * Deletes the group, its chats, our keys, its voice keys and what waits for its state, and notes it was let go of
     * at [version], left or dissolved [byUs] or not. Its messages and their files go once that is committed, by [purge].
     */
    private suspend fun forget(groupId: String, version: Long, byUs: Boolean) = db.withTransaction {
        db.conversations().deleteWithChildren(groupId)
        db.groups().delete(groupId)
        db.groups().deleteSenderKeys(groupId)
        db.groups().deleteVoiceKeys(groupId)
        db.groups().deleteHeld(groupId)
        db.groups().markLeft(LeftGroupEntity(groupId, version, byUs))
    }

    /**
     * Takes off the phone the messages of [groupId] the stored state does not let us read, files and search entries
     * with them: those of channels we do not see or that are gone, and all of a group let go of. Runs after the
     * commit that changed the state, so files go only with rows that are gone for good; what a crash leaves between
     * the two, [tidy] takes on the next start. An id this phone never had as a group is left alone: it comes from
     * whoever asked to join or sent a state, and may name one of our other chats.
     */
    private suspend fun purge(groupId: String) {
        val state = state(groupId)
        if (state == null && db.groups().leftGroup(groupId) == null) return
        val kept = state?.let { channelsWeRead(it).map { channel -> channelConversation(groupId, channel.id) } }.orEmpty()
        val lost = db.messages().channelsWithMessages(groupId).filter { it !in kept } + listOfNotNull(groupId.takeIf { state == null })
        if (lost.isEmpty()) return
        val withFiles = db.withTransaction {
            lost.flatMap { db.messages().withFilesIn(it) }.also { lost.forEach { db.messages().deleteConversation(it) } }
        }
        if (withFiles.isNotEmpty()) purged(withFiles)
        // The room's line in the chat list may have come from a channel just taken away.
        if (state != null) showLast(groupId, kept + groupId)
    }

    private suspend fun showLast(groupId: String, conversationIds: List<String>) {
        val last = db.messages().lastIn(conversationIds)
        val author = last?.authorId?.takeIf { it != me }?.let { id -> db.contacts().get(id)?.name?.substringBefore(' ') ?: id }
        val text = last?.let { it.text.ifEmpty { labels(decodeAttachment(it.attachment)) } }.orEmpty()
        db.conversations().setPreview(groupId, author, text, last?.sentAt ?: 0)
    }

    private fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

    private fun newCode(bytes: Int = INVITE_BYTES): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(bytes).also(random::nextBytes))

    companion object {
        const val CHANNEL_SEPARATOR = ':'
        private const val MAX_TITLE = 64
        private const val MAX_CHANNELS = 20
        private const val INVITE_BYTES = 12

        /** How long a message waits for the state it was written in. */
        private const val HOLD_MILLIS = 7L * 24 * 60 * 60 * 1000
        const val DEFAULT_TEXT_CHANNEL = "общее"
        const val DEFAULT_VOICE_CHANNEL = "голосовой"

        fun channelConversation(groupId: String, channelId: String) = "$groupId$CHANNEL_SEPARATOR$channelId"

        /**
         * Whether [id] can name a group: a UUID as [create] makes it. Anyone may add us to a group, and one named like
         * another of our chats, after a person, the «Избранное» or an open room's prefix, would take it over: what we
         * write there would go to the group, and leaving it would take that chat off the phone.
         */
        fun isGroupId(id: String): Boolean = runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false)
    }
}
