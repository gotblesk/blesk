package app.blesk.core.messaging

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import app.blesk.core.crypto.AttachmentCipher
import app.blesk.core.crypto.StreamingCipher
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.StickerFormat
import app.blesk.core.data.settings.AutoKind
import app.blesk.core.media.MediaVault
import app.blesk.core.media.StickerMaker
import app.blesk.core.network.BleskApi
import app.blesk.shared.appearance.ThemeFile
import app.blesk.shared.protocol.AttachmentPointer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.Base64

/**
 * Moves attachments between the phone and the server. Files are encrypted on the phone with a
 * fresh key before upload; the server stores only ciphertext. Small posters for videos and
 * circles ride inside the (already encrypted) message so the chat can show them at once.
 */
internal object Attachments {
    private const val MAX_INLINE_POSTER = 96 * 1024

    /**
     * Encrypts [attachment] with a fresh streaming key and uploads it in chunks, reporting under
     * [tag]; the ciphertext waits in a temporary file so an interrupted upload can continue.
     */
    suspend fun upload(
        api: BleskApi,
        attachment: Attachment,
        transfers: Transfers = Transfers(),
        tag: String? = null,
    ): AttachmentPointer? {
        if (attachment is Attachment.Theme) {
            return AttachmentPointer(id = "", key = "", kind = KIND_THEME, name = ThemeFile(theme = attachment.spec).encode())
        }
        val source = File(pathOf(attachment) ?: return null)
        val key = StreamingCipher.newKey()
        val encrypted = File.createTempFile("upload", ".bin")
        try {
            encrypted.outputStream().use { out -> source.inputStream().use { StreamingCipher.encrypt(key, it, out) } }
            val id = transfers.send(api, encrypted, tag)
            return pointerFor(attachment, id, b64(key)).copy(scheme = SCHEME_STREAMING, size = source.length())
        } finally {
            encrypted.delete()
        }
    }

    private fun pointerFor(attachment: Attachment, id: String, key: String): AttachmentPointer = when (attachment) {
        is Attachment.Photo -> AttachmentPointer(
            id, key, KIND_PHOTO,
            width = attachment.width, height = attachment.height, thumbnail = tinyPreview(attachment.path),
        )
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
        is Attachment.Music -> AttachmentPointer(
            id, key, KIND_MUSIC,
            name = attachment.name, mime = attachment.mime, size = attachment.sizeBytes,
            durationMillis = attachment.durationMillis, title = attachment.title, artist = attachment.artist,
        )
        is Attachment.Sticker -> AttachmentPointer(
            id, key, KIND_STICKER,
            name = attachment.emoji, mime = attachment.format.mime, size = File(attachment.path).length(),
        )
        is Attachment.Theme -> error("Themes travel inline")
        is Attachment.Album -> error("Albums travel as a list of pointers")
    }

    /** Every file of [attachment] uploaded: one pointer, or one per album item in order. */
    suspend fun uploadEach(api: BleskApi, attachment: Attachment, transfers: Transfers, messageId: String): List<AttachmentPointer>? {
        val album = attachment as? Attachment.Album ?: return listOf(upload(api, attachment, transfers, messageId) ?: return null)
        return album.items.mapIndexed { index, item -> upload(api, item, transfers, albumTag(messageId, index)) ?: return null }
    }

    /** Which «Автозагрузка» switch decides on a file of [kind]; voice and round videos always come. */
    fun autoKindOf(kind: String): AutoKind? = when (kind) {
        KIND_PHOTO -> AutoKind.Photos
        KIND_VIDEO -> AutoKind.Videos
        KIND_FILE, KIND_MUSIC -> AutoKind.Files
        else -> null
    }

    /** The progress key of one album item. */
    fun albumTag(messageId: String, index: Int) = "$messageId/$index"

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
        KIND_PHOTO -> Attachment.Photo("", pointer.width, pointer.height, preview = pointer.thumbnail.orEmpty(), sizeBytes = pointer.size)
        KIND_VIDEO -> Attachment.Video(
            "", "", pointer.width, pointer.height, pointer.durationMillis,
            preview = pointer.thumbnail.orEmpty(), sizeBytes = pointer.size,
        )
        KIND_FILE -> Attachment.File("", safeName(pointer.name), pointer.size, pointer.mime ?: "application/octet-stream")
        KIND_VOICE -> Attachment.Voice("", pointer.durationMillis, pointer.waveform.orEmpty())
        KIND_CIRCLE -> Attachment.Circle("", "", pointer.durationMillis)
        KIND_MUSIC -> music(pointer, "")
        KIND_STICKER -> Attachment.Sticker("", stickerEmoji(pointer), stickerFormat(pointer, null))
        else -> null
    }

    /**
     * Downloads, verifies and decrypts; returns the attachment pointing at the local copy. The
     * ciphertext collects in [partials] under the attachment id, so a broken download continues.
     */
    suspend fun download(
        api: BleskApi,
        media: MediaVault,
        pointer: AttachmentPointer,
        transfers: Transfers = Transfers(),
        tag: String? = null,
    ): Attachment? {
        if (pointer.kind == KIND_THEME) return placeholder(pointer)
        val key = if (pointer.key.isEmpty()) null else unb64(pointer.key)
        val streaming = pointer.scheme == SCHEME_STREAMING
        val keyBytes = if (streaming) StreamingCipher.KEY_BYTES else AttachmentCipher.KEY_BYTES
        if (key != null && key.size != keyBytes) return null
        val encrypted = File(media.partials, partialName(pointer.id))
        // Kept on a network error, so the next attempt continues; dropped once used or found broken.
        transfers.fetch(api, pointer.id, encrypted, tag)
        val target = media.newFile(extension(pointer))
        try {
            decrypt(key, streaming, encrypted, target)
        } catch (broken: IOException) {
            target.delete()
            throw broken
        } finally {
            encrypted.delete()
        }
        val poster = pointer.thumbnail?.let { data -> media.newFile("jpg").also { it.writeBytes(unb64(data)) }.path }.orEmpty()
        return local(pointer, target, poster)
    }

    fun partialName(id: String) = id.filter { it.isLetterOrDigit() || it == '-' || it == '_' } + ".part"

    private fun decrypt(key: ByteArray?, streaming: Boolean, encrypted: File, target: File) {
        when {
            key == null -> encrypted.copyTo(target, overwrite = true)
            streaming -> target.outputStream().use { out ->
                encrypted.inputStream().use { StreamingCipher.decrypting(key, it).copyTo(out) }
            }
            else -> target.outputStream().use { AttachmentCipher.decrypt(key, encrypted, it) }
        }
    }

    private fun local(pointer: AttachmentPointer, target: File, poster: String): Attachment? = when (pointer.kind) {
        KIND_PHOTO -> Attachment.Photo(target.path, pointer.width, pointer.height)
        KIND_VIDEO -> Attachment.Video(target.path, poster, pointer.width, pointer.height, pointer.durationMillis)
        KIND_FILE -> Attachment.File(target.path, safeName(pointer.name), target.length(), pointer.mime ?: "application/octet-stream")
        KIND_VOICE -> Attachment.Voice(target.path, pointer.durationMillis, pointer.waveform.orEmpty())
        KIND_CIRCLE -> Attachment.Circle(target.path, poster, pointer.durationMillis)
        KIND_MUSIC -> music(pointer, target.path)
        KIND_STICKER -> sticker(pointer, target)
        else -> null
    }

    /**
     * A sticker that keeps to the limits, or nothing: a Lottie file is parsed by the app, so
     * a big or malformed one from someone else is not let in.
     */
    private fun sticker(pointer: AttachmentPointer, file: File): Attachment.Sticker? {
        val format = stickerFormat(pointer, file)
        val limit = if (format == StickerFormat.Lottie) StickerMaker.MAX_LOTTIE_BYTES else StickerMaker.MAX_BYTES
        val bytes = file.length()
        val valid = bytes <= limit && (format != StickerFormat.Lottie || StickerMaker.formatOf(file) == StickerFormat.Lottie)
        if (!valid) {
            file.delete()
            return null
        }
        return Attachment.Sticker(file.path, stickerEmoji(pointer), format)
    }

    private fun stickerEmoji(pointer: AttachmentPointer) = pointer.name.orEmpty().take(MAX_STICKER_EMOJI)

    /** Lottie by its type; a WebP is animated or still by its own header, once its bytes are here. */
    private fun stickerFormat(pointer: AttachmentPointer, file: File?): StickerFormat = when {
        pointer.mime == StickerFormat.Lottie.mime -> StickerFormat.Lottie
        file != null && StickerMaker.formatOf(file) == StickerFormat.Animated -> StickerFormat.Animated
        else -> StickerFormat.Static
    }

    private fun music(pointer: AttachmentPointer, path: String) = Attachment.Music(
        path = path,
        name = safeName(pointer.name),
        title = pointer.title.orEmpty().take(MAX_TAG).ifEmpty { safeName(pointer.name).substringBeforeLast('.') },
        artist = pointer.artist.orEmpty().take(MAX_TAG),
        durationMillis = pointer.durationMillis,
        sizeBytes = pointer.size,
        mime = pointer.mime ?: "audio/mpeg",
    )

    private fun pathOf(attachment: Attachment): String? = when (attachment) {
        is Attachment.Photo -> attachment.path
        is Attachment.Video -> attachment.path
        is Attachment.File -> attachment.path
        is Attachment.Voice -> attachment.path
        is Attachment.Circle -> attachment.path
        is Attachment.Music -> attachment.path
        is Attachment.Sticker -> attachment.path
        is Attachment.Theme, is Attachment.Album -> null
    }

    private fun extension(pointer: AttachmentPointer) = when (pointer.kind) {
        KIND_PHOTO -> "jpg"
        KIND_VIDEO, KIND_CIRCLE -> "mp4"
        KIND_VOICE -> "audio"
        KIND_STICKER -> if (pointer.mime == StickerFormat.Lottie.mime) "json" else "webp"
        else -> safeName(pointer.name).substringAfterLast('.', "bin").take(8)
    }

    private fun safeName(name: String?) = (name ?: "file").replace(Regex("[/\\\\:*?\"<>|]"), "_").take(120)

    private fun inline(path: String): String? {
        val file = File(path)
        if (!file.exists() || file.length() > MAX_INLINE_POSTER) return null
        return b64(file.readBytes())
    }

    /** A few hundred bytes of the photo at [PREVIEW_SIDE] pixels, enough for a blur before it arrives. */
    private fun tinyPreview(path: String): String? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= PREVIEW_SIDE) sample *= 2
        val small = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val scale = PREVIEW_SIDE.toFloat() / maxOf(small.width, small.height)
        val width = (small.width * scale).toInt().coerceAtLeast(1)
        val height = (small.height * scale).toInt().coerceAtLeast(1)
        val tiny = Bitmap.createScaledBitmap(small, width, height, true)
        b64(ByteArrayOutputStream().also { tiny.compress(Bitmap.CompressFormat.JPEG, PREVIEW_QUALITY, it) }.toByteArray())
    }.getOrNull()

    private fun b64(bytes: ByteArray) = Base64.getEncoder().encodeToString(bytes)

    fun unb64(text: String) = Base64.getDecoder().decode(text)

    const val SCHEME_STREAMING = 2
    private const val PREVIEW_SIDE = 32
    private const val PREVIEW_QUALITY = 60
    internal const val KIND_THEME = "theme"
    internal const val KIND_PHOTO = "photo"
    private const val KIND_VIDEO = "video"
    private const val KIND_FILE = "file"
    private const val KIND_VOICE = "voice"
    private const val KIND_CIRCLE = "circle"
    private const val KIND_MUSIC = "music"
    private const val KIND_STICKER = "sticker"
    private const val MAX_STICKER_EMOJI = 16
    private const val MAX_TAG = 200
}
