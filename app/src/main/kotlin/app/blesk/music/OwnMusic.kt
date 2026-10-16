package app.blesk.music

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import app.blesk.core.crypto.SealedFile
import app.blesk.core.data.model.Attachment
import app.blesk.core.media.MediaVault
import app.blesk.feature.chats.media.CoverSquare
import app.blesk.shared.protocol.TextSafety
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.math.roundToInt

/** A song added from the phone: the file in the media vault and what you call it. */
@Serializable
data class OwnTrack(
    val id: String,
    val path: String,
    val name: String,
    val title: String,
    val artist: String,
    val durationMillis: Long,
    val sizeBytes: Long,
    val mime: String,
    /** The picture from the file's tags, taken out once on import. */
    val tagCover: String? = null,
    /** A cover you picked and cropped; it wins over the tags. */
    val ownCover: String? = null,
    val addedAt: Long,
) {
    val cover: String? get() = ownCover ?: tagCover
}

/**
 * «Музыка → Добавить с телефона»: songs copied into the app, kept with their names and covers.
 * The list is sealed with a key from Android Keystore, so what you listen to is not readable from
 * the app's storage. Nothing is looked up on the internet: no one learns what is in it.
 */
class OwnMusic(context: Context, private val vault: MediaVault, scope: CoroutineScope) {
    private val sealed = SealedFile(File(context.filesDir, "own_music.bin"), "blesk.music")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(OwnTrack.serializer())
    private val lock = Mutex()
    private val state = MutableStateFlow<List<OwnTrack>>(emptyList())

    val tracks: StateFlow<List<OwnTrack>> = state.asStateFlow()

    init {
        scope.launch(Dispatchers.IO) {
            val stored = sealed.read()?.let { runCatching { json.decodeFromString(serializer, it.decodeToString()) }.getOrNull() }
            state.value = stored.orEmpty()
        }
    }

    /** Copies the picked files in; returns how many could not be read as music. */
    suspend fun add(uris: List<Uri>, now: Long): Int {
        var failed = 0
        val added = uris.mapNotNull { uri ->
            when (val imported = vault.importMusic(uri)) {
                is Attachment.Music -> track(imported, now)
                else -> {
                    failed++
                    vault.forget(imported)
                    null
                }
            }
        }
        if (added.isNotEmpty()) change { it + added }
        return failed
    }

    suspend fun rename(id: String, title: String, artist: String) = change { list ->
        list.map {
            if (it.id != id) return@map it
            it.copy(
                title = TextSafety.clean(title).take(MAX_TEXT).ifBlank { it.title },
                artist = TextSafety.clean(artist).take(MAX_TEXT),
            )
        }
    }

    /** Cuts [square] (fractions of the picture) out of the picked photo and makes it the cover. */
    suspend fun setCover(id: String, uri: Uri, square: CoverSquare, resolver: ContentResolver): Boolean {
        val file = withContext(Dispatchers.IO) {
            val picture = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return@withContext null
            val bounds = Rect(
                (square.left * picture.width).roundToInt(),
                (square.top * picture.height).roundToInt(),
                ((square.left + square.width) * picture.width).roundToInt(),
                ((square.top + square.height) * picture.height).roundToInt(),
            ).apply { intersect(0, 0, picture.width, picture.height) }
            if (bounds.width() <= 0 || bounds.height() <= 0) return@withContext null
            val cut = Bitmap.createBitmap(picture, bounds.left, bounds.top, bounds.width(), bounds.height())
            val scaled = Bitmap.createScaledBitmap(cut, COVER_SIDE, COVER_SIDE, true)
            save(scaled)
        } ?: return false
        val old = state.value.firstOrNull { it.id == id }?.ownCover
        change { list -> list.map { if (it.id == id) it.copy(ownCover = file.path) else it } }
        old?.let { File(it).delete() }
        return true
    }

    suspend fun removeCover(id: String) {
        val old = state.value.firstOrNull { it.id == id }?.ownCover ?: return
        change { list -> list.map { if (it.id == id) it.copy(ownCover = null) else it } }
        File(old).delete()
    }

    suspend fun remove(id: String) {
        val gone = state.value.firstOrNull { it.id == id } ?: return
        change { list -> list.filterNot { it.id == id } }
        withContext(Dispatchers.IO) { listOfNotNull(gone.path, gone.tagCover, gone.ownCover).forEach { File(it).delete() } }
    }

    /** A copy for a message, under the names you gave it: deleting the message never touches the library. */
    suspend fun forMessage(track: OwnTrack): Attachment.Music? {
        val copy = vault.copyIn(File(track.path), track.name.substringAfterLast('.', "bin")) ?: return null
        return Attachment.Music(copy.path, track.name, track.title, track.artist, track.durationMillis, track.sizeBytes, track.mime)
    }

    private suspend fun track(music: Attachment.Music, now: Long): OwnTrack {
        val cover = withContext(Dispatchers.IO) {
            runCatching {
                MediaMetadataRetriever().run {
                    try {
                        setDataSource(music.path)
                        embeddedPicture
                    } finally {
                        release()
                    }
                }
            }.getOrNull()?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }?.let(::save)
        }
        return OwnTrack(
            id = UUID.randomUUID().toString(),
            path = music.path,
            name = music.name,
            title = TextSafety.clean(music.title).take(MAX_TEXT),
            artist = TextSafety.clean(music.artist).take(MAX_TEXT),
            durationMillis = music.durationMillis,
            sizeBytes = music.sizeBytes,
            mime = music.mime,
            tagCover = cover?.path,
            addedAt = now,
        )
    }

    private fun save(bitmap: Bitmap): File? {
        val file = vault.newFile("jpg")
        return try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, it) }
            file
        } catch (_: IOException) {
            file.delete()
            null
        }
    }

    private suspend fun change(transform: (List<OwnTrack>) -> List<OwnTrack>) = lock.withLock {
        state.update(transform)
        val bytes = json.encodeToString(serializer, state.value).encodeToByteArray()
        withContext(Dispatchers.IO) { sealed.write(bytes) }
    }

    private companion object {
        const val MAX_TEXT = 120
        const val COVER_SIDE = 512
        const val COVER_QUALITY = 88
    }
}
