package app.blesk.core.messaging

import app.blesk.core.crypto.AttachmentCipher
import app.blesk.core.data.model.Attachment
import app.blesk.core.media.MediaVault
import app.blesk.core.network.BleskApi
import app.blesk.shared.appearance.ThemeFile
import app.blesk.shared.protocol.AttachmentPointer
import java.io.File
import java.util.Base64

/**
 * Moves attachments between the phone and the server. Files are encrypted on the phone with a
 * fresh key before upload; the server stores only ciphertext. Small posters for videos and
 * circles ride inside the (already encrypted) message so the chat can show them at once.
 */
internal object Attachments {
    private const val MAX_INLINE_POSTER = 96 * 1024

    suspend fun upload(api: BleskApi, attachment: Attachment): AttachmentPointer? {
        if (attachment is Attachment.Theme) {
            return AttachmentPointer(id = "", key = "", kind = KIND_THEME, name = ThemeFile(theme = attachment.spec).encode())
        }
        val source = File(pathOf(attachment) ?: return null)
        val key = AttachmentCipher.newKey()
        val encrypted = File.createTempFile("upload", ".bin")
        try {
            encrypted.outputStream().use { out -> source.inputStream().use { AttachmentCipher.encrypt(key, it, out) } }
            val id = api.uploadAttachment(encrypted).id
            return pointerFor(attachment, id, b64(key))
        } finally {
            encrypted.delete()
        }
    }

    private fun pointerFor(attachment: Attachment, id: String, key: String): AttachmentPointer = when (attachment) {
        is Attachment.Photo -> AttachmentPointer(id, key, KIND_PHOTO, width = attachment.width, height = attachment.height)
        is Attachment.Video -> AttachmentPointer(
            id, key, KIND_VIDEO,
            width = attachment.width, height = attachment.height, durationMillis = attachment.durationMillis,
            thumbnail = inline(attachment.thumbnail),
        )
        is Attachment.File -> AttachmentPointer(
            id, key, KIND_FILE,
            name = attachment.name, mime = attachment.mime, size = attachment.sizeBytes,
        )
        is Attachment.Voice -> AttachmentPointer(
            id, key, KIND_VOICE,
            durationMillis = attachment.durationMillis, waveform = attachment.waveform,
        )
        is Attachment.Circle -> AttachmentPointer(
            id, key, KIND_CIRCLE,
            durationMillis = attachment.durationMillis, thumbnail = inline(attachment.poster),
        )
        is Attachment.Theme -> error("Themes travel inline")
        is Attachment.Album -> error("Albums travel as a list of pointers")
    }

    /** Every file of [attachment] uploaded: one pointer, or one per album item in order. */
    suspend fun uploadEach(api: BleskApi, attachment: Attachment): List<AttachmentPointer>? {
        val parts = (attachment as? Attachment.Album)?.items ?: listOf(attachment)
        return parts.map { upload(api, it) ?: return null }
    }

    /** An album before its files arrive: the size of each item, so the mosaic is laid out at once. */
    fun placeholders(pointers: List<AttachmentPointer>): Attachment.Album =
        Attachment.Album(pointers.filter { it.kind == KIND_PHOTO || it.kind == KIND_VIDEO }.mapNotNull(::placeholder))

    /** For public spaces: uploaded as is, with an empty key, because those are not encrypted anyway. */
    suspend fun uploadPlain(api: BleskApi, attachment: Attachment): AttachmentPointer? {
        if (attachment is Attachment.Theme) return upload(api, attachment)
        val source = File(pathOf(attachment) ?: return null)
        val id = api.uploadAttachment(source).id
        return pointerFor(attachment, id, key = "")
    }

    /** What the chat shows before the file has arrived: everything but the bytes. */
    fun placeholder(pointer: AttachmentPointer): Attachment? = when (pointer.kind) {
        KIND_THEME -> pointer.name?.let(ThemeFile::decode)?.let { Attachment.Theme(it.theme) }
        KIND_PHOTO -> Attachment.Photo("", pointer.width, pointer.height)
        KIND_VIDEO -> Attachment.Video("", "", pointer.width, pointer.height, pointer.durationMillis)
        KIND_FILE -> Attachment.File("", safeName(pointer.name), pointer.size, pointer.mime ?: "application/octet-stream")
        KIND_VOICE -> Attachment.Voice("", pointer.durationMillis, pointer.waveform.orEmpty())
        KIND_CIRCLE -> Attachment.Circle("", "", pointer.durationMillis)
        else -> null
    }

    /** Downloads, verifies and decrypts; returns the attachment pointing at the local copy. */
    suspend fun download(api: BleskApi, media: MediaVault, pointer: AttachmentPointer): Attachment? {
        if (pointer.kind == KIND_THEME) return placeholder(pointer)
        val key = if (pointer.key.isEmpty()) null else unb64(pointer.key)
        if (key != null && key.size != AttachmentCipher.KEY_BYTES) return null
        val encrypted = File.createTempFile("download", ".bin")
        try {
            encrypted.outputStream().use { api.downloadAttachment(pointer.id, it) }
            val target = media.newFile(extension(pointer))
            if (key == null) {
                encrypted.copyTo(target, overwrite = true)
            } else {
                target.outputStream().use { AttachmentCipher.decrypt(key, encrypted, it) }
            }
            val poster = pointer.thumbnail?.let { data -> media.newFile("jpg").also { it.writeBytes(unb64(data)) }.path }.orEmpty()
            return when (pointer.kind) {
                KIND_PHOTO -> Attachment.Photo(target.path, pointer.width, pointer.height)
                KIND_VIDEO -> Attachment.Video(target.path, poster, pointer.width, pointer.height, pointer.durationMillis)
                KIND_FILE -> Attachment.File(
                    target.path, safeName(pointer.name), target.length(), pointer.mime ?: "application/octet-stream",
                )
                KIND_VOICE -> Attachment.Voice(target.path, pointer.durationMillis, pointer.waveform.orEmpty())
                KIND_CIRCLE -> Attachment.Circle(target.path, poster, pointer.durationMillis)
                else -> null
            }
        } finally {
            encrypted.delete()
        }
    }

    private fun pathOf(attachment: Attachment): String? = when (attachment) {
        is Attachment.Photo -> attachment.path
        is Attachment.Video -> attachment.path
        is Attachment.File -> attachment.path
        is Attachment.Voice -> attachment.path
        is Attachment.Circle -> attachment.path
        is Attachment.Theme, is Attachment.Album -> null
    }

    private fun extension(pointer: AttachmentPointer) = when (pointer.kind) {
        KIND_PHOTO -> "jpg"
        KIND_VIDEO, KIND_CIRCLE -> "mp4"
        KIND_VOICE -> "audio"
        else -> safeName(pointer.name).substringAfterLast('.', "bin").take(8)
    }

    private fun safeName(name: String?) = (name ?: "file").replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)

    private fun inline(path: String): String? {
        val file = File(path)
        if (!file.exists() || file.length() > MAX_INLINE_POSTER) return null
        return b64(file.readBytes())
    }

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    private fun unb64(text: String) = Base64.getDecoder().decode(text)

    private const val KIND_THEME = "theme"
    internal const val KIND_PHOTO = "photo"
    private const val KIND_VIDEO = "video"
    private const val KIND_FILE = "file"
    private const val KIND_VOICE = "voice"
    private const val KIND_CIRCLE = "circle"
}
