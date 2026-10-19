package app.blesk.core.messaging

import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.db.ConversationEntity
import app.blesk.core.data.db.MessageEntity
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Protection
import app.blesk.core.data.model.SendStatus
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.Post
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Public spaces mirrored into the local database so they look and behave like any other
 * conversation. Nothing here is end-to-end encrypted, and the screens say so.
 */
internal class SpaceEngine(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val media: MediaVault,
    private val me: String,
    private val labels: (Attachment?) -> String,
) {
    private val known = MutableStateFlow<Map<String, SpaceInfo>>(emptyMap())

    val spaces: StateFlow<Map<String, SpaceInfo>> = known.asStateFlow()

    /** Held from the check that a post's version is the newest until it is written, so no other write gets between. */
    private val postWrites = Mutex()

    /**
     * While a sync runs, the newest version written to each post: its edit time, or [REMOVED]. A sync stores posts
     * fetched before the live edits and removals that come meanwhile, and its older copy must not undo them. This map
     * and the count of [syncs] running are guarded by the map's monitor.
     */
    private val newest = HashMap<String, Long>()
    private var syncs = 0

    fun handleOf(conversationId: String): String? =
        conversationId.takeIf { it.startsWith(PREFIX) }?.removePrefix(PREFIX)?.substringBefore(SEPARATOR)

    private fun channelOf(conversationId: String): String =
        conversationId.removePrefix(PREFIX).substringAfter(SEPARATOR, "").ifEmpty { NewPost.MAIN_CHANNEL }

    /** Re-reads the spaces you are in and their latest posts; called on every (re)connection. */
    suspend fun sync() {
        synchronized(newest) { syncs++ }
        try {
            val mine = api.mySpaces()
            val handles = mine.map { it.handle }.toSet()
            known.value.keys.filter { it !in handles }.forEach { forget(it) }
            mine.forEach { syncSpace(it) }
        } finally {
            synchronized(newest) { if (--syncs == 0) newest.clear() }
        }
    }

    private suspend fun syncSpace(info: SpaceInfo) {
        remember(info)
        channelsOf(info).forEach { channel ->
            api.posts(info.handle, channel).asReversed().forEach { store(it) }
        }
    }

    suspend fun remember(info: SpaceInfo) {
        known.update { it + (info.handle to info) }
        val rootId = PREFIX + info.handle
        val kind = if (info.kind == SpaceKind.Channel) ConversationKind.Channel else ConversationKind.Room
        db.conversations().insertIfAbsent(
            ConversationEntity(rootId, kind.name, info.title, Protection.Open.name, null, System.currentTimeMillis(), previewText = ""),
        )
        db.conversations().setTitle(rootId, info.title)
        if (info.kind != SpaceKind.Room) return
        val ids = info.channels.map { conversation(info.handle, it.id) }
        db.conversations().children(rootId).filter { it.id !in ids }.forEach { db.conversations().deleteWithChildren(it.id) }
        info.channels.forEach { channel ->
            val id = conversation(info.handle, channel.id)
            db.conversations().insertIfAbsent(
                ConversationEntity(id, kind.name, channel.title, Protection.Open.name, null, System.currentTimeMillis(), parentId = rootId),
            )
            db.conversations().setTitle(id, channel.title)
        }
    }

    /**
     * Keeps [post]; returns its conversation when it is new here, so it can be announced. A post already here gets only
     * what the server owns: its file may be downloading or being cleared right now, and that write must stand. An older
     * version than one written during a sync, or a post removed meanwhile, is not stored.
     */
    suspend fun store(post: Post): String? {
        val conversationId =
            if (known.value[post.space]?.kind == SpaceKind.Room) conversation(post.space, post.channel) else PREFIX + post.space
        val id = messageId(post.space, post.id)
        val replyTo = post.replyTo?.let { messageId(post.space, it) }
        val edited = post.editedAtMillis != null
        val placeholder = post.attachment?.let(Attachments::placeholder)
        val fresh = MessageEntity(
            id = id,
            conversationId = conversationId,
            authorId = post.author,
            text = post.text,
            sentAt = post.createdAtMillis,
            status = SendStatus.Read.name,
            replyToId = replyTo,
            edited = edited,
            attachment = encodeAttachment(placeholder),
        )
        val added = postWrites.withLock {
            when {
                !takeNewest(id, post.editedAtMillis ?: UNEDITED) -> false
                db.messages().insert(fresh) == ALREADY_STORED -> {
                    db.messages().setPost(id, conversationId, post.text, edited, replyTo)
                    false
                }
                else -> true
            }
        }
        if (!added) return null
        val preview = post.text.ifEmpty { labels(placeholder) }
        db.conversations().setPreview(conversationId, post.author.takeIf { it != me }, preview, post.createdAtMillis)
        db.conversations().get(conversationId)?.parentId?.let {
            db.conversations().setPreview(it, post.author, preview, post.createdAtMillis)
        }
        post.attachment?.let { pointer ->
            val local = runCatching { withContext(Dispatchers.IO) { Attachments.download(api, media, pointer) } }.getOrNull()
            db.messages().setAttachment(id, encodeAttachment(local))
        }
        return conversationId
    }

    suspend fun removed(space: String, id: Long) {
        val messageId = messageId(space, id)
        postWrites.withLock {
            if (takeNewest(messageId, REMOVED)) db.messages().delete(messageId)
        }
    }

    /** Whether [version] of a post is no older than any written during a sync; if so, it is the newest from now on. */
    private fun takeNewest(messageId: String, version: Long): Boolean = synchronized(newest) {
        if ((newest[messageId] ?: version) > version) return false
        if (syncs > 0) newest[messageId] = version
        true
    }

    suspend fun send(conversationId: String, text: String, replyToId: String?, attachment: Attachment?): Boolean {
        val handle = handleOf(conversationId) ?: return false
        val pointer = attachment?.let { withContext(Dispatchers.IO) { Attachments.uploadPlain(api, it) } ?: return false }
        val post = api.post(
            handle,
            NewPost(UUID.randomUUID().toString(), text.trim(), channelOf(conversationId), replyToId?.let(::postId), pointer),
        )
        store(post)
        return true
    }

    suspend fun edit(messageId: String, text: String) {
        val (handle, id) = split(messageId) ?: return
        store(api.editPost(handle, id, text))
    }

    suspend fun delete(messageId: String) {
        val (handle, id) = split(messageId) ?: return
        api.deletePost(handle, id)
        removed(handle, id)
    }

    suspend fun forget(handle: String) {
        val root = PREFIX + handle
        db.conversations().children(root).forEach { db.messages().deleteConversation(it.id) }
        db.messages().deleteConversation(root)
        db.conversations().deleteWithChildren(root)
        known.update { it - handle }
    }

    private fun channelsOf(info: SpaceInfo) = if (info.kind == SpaceKind.Room) info.channels.map { it.id } else listOf(NewPost.MAIN_CHANNEL)

    private fun split(messageId: String): Pair<String, Long>? {
        val parts = messageId.removePrefix(POST_PREFIX).split(SEPARATOR)
        if (!messageId.startsWith(POST_PREFIX) || parts.size != 2) return null
        return parts[1].toLongOrNull()?.let { parts[0] to it }
    }

    private fun postId(messageId: String): Long? = split(messageId)?.second

    companion object {
        const val PREFIX = "space:"
        private const val POST_PREFIX = "post:"
        private const val SEPARATOR = ':'

        /** What an insert that kept the row already there returns. */
        private const val ALREADY_STORED = -1L

        /** The version of a post never edited, older than any edit; a removal is newer than all of them. */
        private const val UNEDITED = 0L
        private const val REMOVED = Long.MAX_VALUE

        fun conversation(handle: String, channel: String) = "$PREFIX$handle$SEPARATOR$channel"

        fun messageId(handle: String, id: Long) = "$POST_PREFIX$handle$SEPARATOR$id"
    }
}
