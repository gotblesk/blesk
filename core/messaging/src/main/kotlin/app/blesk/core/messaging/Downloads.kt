package app.blesk.core.messaging

import app.blesk.core.data.db.BleskDatabase
import app.blesk.core.data.model.Attachment
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.AttachmentPointer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Brings the files of a received message onto the phone and points the stored message at them. */
internal class Downloads(private val db: BleskDatabase, private val api: BleskApi, private val media: MediaVault) {

    /** One file, or an album item by item, each showing up in the mosaic as soon as it is here. */
    suspend fun fetch(messageId: String, single: AttachmentPointer?, album: List<AttachmentPointer>) {
        single?.let { pointer ->
            val local = download(pointer) ?: return
            val stored = db.messages().get(messageId) ?: return
            db.messages().upsert(stored.copy(attachment = encodeAttachment(local)))
        }
        album.forEachIndexed { index, pointer ->
            val local = download(pointer) ?: return@forEachIndexed
            val stored = db.messages().get(messageId) ?: return
            val current = decodeAttachment(stored.attachment) as? Attachment.Album ?: return
            val items = current.items.toMutableList().apply { if (index < size) set(index, local) }
            db.messages().upsert(stored.copy(attachment = encodeAttachment(Attachment.Album(items))))
        }
    }

    private suspend fun download(pointer: AttachmentPointer): Attachment? =
        runCatching { withContext(Dispatchers.IO) { Attachments.download(api, media, pointer) } }.getOrNull()
}
