package app.blesk.core.messaging

import app.blesk.core.crypto.StreamingCipher
import app.blesk.core.data.StreamSource
import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.model.Attachment
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.AttachmentPointer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/** Where the files of a received message lie on the server, until they are all on the phone. */
@Serializable
internal data class RemoteFiles(val single: AttachmentPointer? = null, val album: List<AttachmentPointer> = emptyList()) {
    fun encode(): String = storageJson.encodeToString(serializer(), this)

    companion object {
        fun decode(raw: String?): RemoteFiles? = raw?.let { runCatching { storageJson.decodeFromString(serializer(), it) }.getOrNull() }
    }
}

/**
 * Brings the files of received messages onto the phone and points the stored message at them.
 * A download can be cancelled and started again; it continues from the bytes already here.
 */
internal class Downloads(
    private val db: BleskDatabase,
    private val api: BleskApi,
    private val media: MediaVault,
    private val transfers: Transfers,
    private val scope: CoroutineScope,
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun start(messageId: String) {
        if (jobs[messageId]?.isActive == true) return
        jobs[messageId] = scope.launch { run(messageId) }
    }

    fun cancel(messageId: String): Boolean {
        val job = jobs.remove(messageId) ?: return false
        job.cancel()
        scope.launch {
            val remote = RemoteFiles.decode(db.messages().get(messageId)?.remote) ?: return@launch
            if (remote.single != null) transfers.fail(messageId)
            remote.album.indices.forEach { transfers.fail(Attachments.albumTag(messageId, it)) }
        }
        return true
    }

    /** A video of [messageId] that can play while it arrives; its download starts if it has not. */
    suspend fun stream(messageId: String, index: Int): StreamSource? {
        val remote = RemoteFiles.decode(db.messages().get(messageId)?.remote) ?: return null
        val pointer = remote.single?.takeIf { index == 0 } ?: remote.album.getOrNull(index) ?: return null
        if (pointer.scheme != Attachments.SCHEME_STREAMING || pointer.key.isEmpty()) return null
        val key = Attachments.unb64(pointer.key)
        start(messageId)
        val ciphertext = File(media.partials, Attachments.partialName(pointer.id))
        return StreamSource(ciphertext, key, StreamingCipher.ciphertextSize(key, pointer.size))
    }

    /** One file, or an album item by item, each showing up in the mosaic as soon as it is here. */
    private suspend fun run(messageId: String) {
        val remote = RemoteFiles.decode(db.messages().get(messageId)?.remote) ?: return
        var complete = true
        remote.single?.let { pointer ->
            val local = download(pointer, messageId)
            val stored = db.messages().get(messageId) ?: return
            if (local == null) complete = false else db.messages().upsert(stored.copy(attachment = encodeAttachment(local)))
        }
        remote.album.forEachIndexed { index, pointer ->
            val stored = db.messages().get(messageId) ?: return
            val current = decodeAttachment(stored.attachment) as? Attachment.Album ?: return
            if (current.items.getOrNull(index)?.let(::arrived) == true) return@forEachIndexed
            val local = download(pointer, Attachments.albumTag(messageId, index))
            if (local == null) {
                complete = false
                return@forEachIndexed
            }
            val fresh = db.messages().get(messageId) ?: return
            val items = (decodeAttachment(fresh.attachment) as? Attachment.Album ?: return).items.toMutableList()
            if (index < items.size) items[index] = local
            db.messages().upsert(fresh.copy(attachment = encodeAttachment(Attachment.Album(items))))
        }
        if (complete) db.messages().get(messageId)?.let { db.messages().upsert(it.copy(remote = null)) }
        jobs.remove(messageId)
    }

    private fun arrived(item: Attachment) = when (item) {
        is Attachment.Photo -> item.path.isNotEmpty()
        is Attachment.Video -> item.path.isNotEmpty()
        else -> true
    }

    private suspend fun download(pointer: AttachmentPointer, tag: String): Attachment? =
        try {
            withContext(Dispatchers.IO) { Attachments.download(api, media, pointer, transfers, tag) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            transfers.fail(tag)
            null
        }
}
