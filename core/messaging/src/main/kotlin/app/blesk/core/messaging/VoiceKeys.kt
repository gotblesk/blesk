package app.blesk.core.messaging

import app.blesk.core.data.calls.VoiceAccess
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.VoiceKeyEntity
import app.blesk.shared.protocol.Body
import app.blesk.shared.protocol.Calls
import app.blesk.shared.protocol.Content
import app.blesk.shared.protocol.GroupChannel
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * The media keys and LiveKit rooms of closed voice channels. A voice channel open to the whole room uses the key in the
 * room's state, which every member gets; a closed one cannot. So one admin, the keeper, makes each closed channel a key
 * and a room of its own, hands them one-to-one only to those who see it, and replaces both once someone who got them
 * no longer does.
 */
internal class VoiceKeys(
    private val db: BleskDatabase,
    private val me: String,
    private val outbox: GroupOutbox,
    private val clock: () -> Long,
) {
    private val random = SecureRandom()

    /**
     * As the keeper of [state]'s room, gives everyone who sees one of its closed voice channels, and no one else, the
     * channel's key. A key someone got who no longer sees the channel, or one from another epoch, is not handed out
     * again: a new one, for a new room, takes its place. Runs in the transaction that stored [state], after the update
     * telling members of it was queued, so each key goes out behind the state it is for.
     */
    suspend fun keep(state: GroupState) {
        if (keeper(state) != me) return
        state.channels.filter { it.kind == GroupChannelKind.Voice && hasOwnKey(it) }.forEach { channel ->
            val readers = GroupPolicy.readers(state, GroupEngine.channelConversation(state.id, channel.id)) - me
            val row = db.groups().voiceKey(state.id, channel.id)
            val holders = row?.takeIf { it.epoch == state.epoch }?.holders?.let(::decodeNames)?.takeIf { readers.containsAll(it) }
            val current = if (row != null && holders != null) row else newKey(state, channel.id, after = row?.version ?: 0)
            val missing = readers - holders.orEmpty().toSet()
            if (current == row && missing.isEmpty()) return@forEach
            val key = Body.VoiceKey(state.id, channel.id, current.version, current.room, current.key)
            missing.forEach { outbox.queue(it, Content(UUID.randomUUID().toString(), clock(), key)) }
            db.groups().upsertVoiceKey(current.copy(holders = encodeNames(holders.orEmpty() + missing)))
        }
    }

    /**
     * A key from [sender], taken only from an admin of its room. Kept for any channel, one this phone does not see or
     * know yet too: it may arrive ahead of the state that shows us the channel. [stored] is used only where we see it.
     */
    suspend fun accept(sender: String, key: Body.VoiceKey) {
        val state = db.groups().get(key.groupId)?.let { storageJson.decodeFromString(GroupState.serializer(), it.state) } ?: return
        if (!state.isAdmin(sender) || !Calls.isClosedVoiceRoom(key.room, key.groupId, key.channelId) || !isMediaKey(key.key)) return
        db.groups().offerVoiceKey(
            VoiceKeyEntity(key.groupId, key.channelId, key.version, key.room, key.key, state.epoch, giver = sender),
            keeper(state),
        )
    }

    /**
     * A closed voice channel's key as this phone has it, made here or given by someone [state] still has as an admin:
     * one from an admin since let go of may have been made to outlast them. The caller says whether we see the channel.
     */
    suspend fun stored(state: GroupState, channelId: String): VoiceAccess? {
        val row = db.groups().voiceKey(state.id, channelId) ?: return null
        val giver = row.giver
        return if (giver == null || state.isAdmin(giver)) VoiceAccess(row.room, row.key) else null
    }

    fun keeps(state: GroupState) = keeper(state) == me

    fun newMediaKey(): String = Base64.getEncoder().encodeToString(ByteArray(Calls.KEY_BYTES).also(random::nextBytes))

    /** The admin who replaces voice keys: the first in the member list, normally the owner. */
    private fun keeper(state: GroupState): String? = state.members.firstOrNull { state.isAdmin(it.username) }?.username

    /**
     * A new key of a closed voice channel, for a new room. Its version is the time it is made, or one past [after] if
     * that is later: a keeper new to the channel, one that never got its last key, still makes a key the others take.
     */
    private fun newKey(state: GroupState, channelId: String, after: Long): VoiceKeyEntity {
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(ByteArray(Calls.ROOM_SECRET_BYTES).also(random::nextBytes))
        return VoiceKeyEntity(
            groupId = state.id,
            channelId = channelId,
            version = maxOf(clock(), after + 1),
            room = Calls.closedVoiceRoom(state.id, channelId, secret),
            key = newMediaKey(),
            epoch = state.epoch,
            holders = encodeNames(emptyList()),
        )
    }

    private fun isMediaKey(key: String): Boolean =
        runCatching { Base64.getDecoder().decode(key).size == Calls.KEY_BYTES }.getOrDefault(false)

    companion object {
        /** Decided by the channel alone, as [GroupPolicy.keyScope] decides for text: one closed to any role has its own. */
        fun hasOwnKey(channel: GroupChannel) = channel.hiddenFrom.isNotEmpty()
    }
}
