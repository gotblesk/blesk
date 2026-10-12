package app.blesk.core.data.stickers

import android.content.Context
import app.blesk.core.data.model.StickerFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * The sticker library in the app's private storage: a folder per set and one index file. It does
 * not depend on the account, so the demo and a server account see the same stickers. Sets bundled
 * with the app are copied out of the assets the first time they are seen.
 */
class StickerStore(private val context: Context) {
    private val root = File(context.filesDir, "stickers")
    private val index = File(root, "library.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val lock = Mutex()
    private val state = MutableStateFlow(StickerLibrary())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val library: StateFlow<StickerLibrary> = state.asStateFlow()

    init {
        scope.launch { lock.withLock { state.value = resolved(withBundled(read())) } }
    }

    suspend fun createSet(title: String): StickerSet = change { library ->
        val set = StickerSet(id = UUID.randomUUID().toString(), title = title.trim())
        File(root, set.id).mkdirs()
        library.copy(sets = library.sets + set) to set
    }

    suspend fun renameSet(id: String, title: String) = change { library ->
        library.copy(sets = library.sets.map { if (it.id == id && !it.bundled) it.copy(title = title.trim()) else it }) to Unit
    }

    suspend fun deleteSet(id: String) = change { library ->
        val set = library.sets.firstOrNull { it.id == id && !it.bundled } ?: return@change library to Unit
        File(root, set.id).deleteRecursively()
        val gone = set.stickers.map { it.id }.toSet()
        library.copy(
            sets = library.sets - set,
            favorites = library.favorites - gone,
            recent = library.recent - gone,
        ) to Unit
    }

    /** Moves the prepared [source] file into the set; null when the set is full or not editable. */
    suspend fun add(setId: String, source: File, emoji: String, caption: String, format: StickerFormat): Sticker? = change { library ->
        val set = library.sets.firstOrNull { it.id == setId && !it.bundled && !it.full } ?: return@change library to null
        val id = UUID.randomUUID().toString()
        val name = "$id.${format.extension}"
        val target = File(File(root, set.id).apply { mkdirs() }, name)
        if (!source.renameTo(target)) {
            source.copyTo(target, overwrite = true)
            source.delete()
        }
        val sticker =
            Sticker(id = id, file = name, emoji = emoji.trim(), caption = caption.trim(), format = format, path = target.path)
        library.withSet(set.copy(stickers = set.stickers + sticker)) to sticker
    }

    suspend fun edit(stickerId: String, emoji: String, caption: String) = change { library ->
        val set = library.ownerOf(stickerId)?.takeIf { !it.bundled } ?: return@change library to Unit
        val stickers = set.stickers.map { if (it.id == stickerId) it.copy(emoji = emoji.trim(), caption = caption.trim()) else it }
        library.withSet(set.copy(stickers = stickers)) to Unit
    }

    suspend fun remove(stickerId: String) = change { library ->
        val set = library.ownerOf(stickerId)?.takeIf { !it.bundled } ?: return@change library to Unit
        val sticker = set.stickers.first { it.id == stickerId }
        File(sticker.path).delete()
        library.withSet(set.copy(stickers = set.stickers - sticker))
            .copy(favorites = library.favorites - stickerId, recent = library.recent - stickerId) to Unit
    }

    suspend fun toggleFavorite(stickerId: String) = change { library ->
        val favorites = if (stickerId in library.favorites) library.favorites - stickerId else listOf(stickerId) + library.favorites
        library.copy(favorites = favorites) to Unit
    }

    suspend fun markUsed(stickerId: String) = change { library ->
        library.copy(recent = (listOf(stickerId) + (library.recent - stickerId)).take(StickerLibrary.MAX_RECENT)) to Unit
    }

    private suspend fun <T> change(block: (StickerLibrary) -> Pair<StickerLibrary, T>): T = withContext(Dispatchers.IO) {
        lock.withLock {
            val (next, result) = block(state.value)
            if (next !== state.value) {
                write(next)
                state.value = resolved(next)
            }
            result
        }
    }

    private fun read(): StickerLibrary = try {
        if (index.exists()) json.decodeFromString(StickerLibrary.serializer(), index.readText()) else StickerLibrary()
    } catch (_: SerializationException) {
        StickerLibrary()
    } catch (_: IOException) {
        StickerLibrary()
    }

    /** Written whole to a side file and swapped in, so a crash halfway never leaves half an index. */
    private fun write(library: StickerLibrary) {
        root.mkdirs()
        val next = File(root, "library.json.new")
        next.writeText(json.encodeToString(StickerLibrary.serializer(), library))
        if (!next.renameTo(index)) next.copyTo(index, overwrite = true).also { next.delete() }
    }

    private fun resolved(library: StickerLibrary) = library.copy(
        sets = library.sets.map { set ->
            set.copy(stickers = set.stickers.map { it.copy(path = File(File(root, set.id), it.file).path) })
        },
    )

    /** Adds the app's own sets that are not in the library yet; their files are copied once. */
    private fun withBundled(library: StickerLibrary): StickerLibrary {
        val names = runCatching { context.assets.list(ASSETS).orEmpty().toList() }.getOrDefault(emptyList())
        val added = names.mapNotNull { name ->
            val id = "$BUNDLED_PREFIX$name"
            if (library.sets.any { it.id == id }) return@mapNotNull null
            runCatching { copyBundled(name, id) }.getOrNull()
        }
        if (added.isEmpty()) return library
        return library.copy(sets = added + library.sets).also(::write)
    }

    private fun copyBundled(name: String, id: String): StickerSet {
        val manifest = context.assets.open("$ASSETS/$name/$MANIFEST").bufferedReader().use { it.readText() }
        val bundled = json.decodeFromString(BundledSet.serializer(), manifest)
        val folder = File(root, id).apply { mkdirs() }
        val stickers = bundled.stickers.map { entry ->
            context.assets.open("$ASSETS/$name/${entry.file}").use { input -> File(folder, entry.file).outputStream().use(input::copyTo) }
            val format = if (entry.file.endsWith(".json")) StickerFormat.Lottie else StickerFormat.Animated
            Sticker(id = "$id/${entry.file}", file = entry.file, emoji = entry.emoji, format = format)
        }
        return StickerSet(id = id, title = bundled.title, stickers = stickers, bundled = true)
    }

    private companion object {
        const val ASSETS = "stickers"
        const val MANIFEST = "set.json"
        const val BUNDLED_PREFIX = "app-"
    }
}

/** `assets/stickers/<name>/set.json`: the title and, for each file, the emoji it stands for. */
@Serializable
private data class BundledSet(val title: String, val stickers: List<BundledSticker>)

@Serializable
private data class BundledSticker(val file: String, val emoji: String)

private fun StickerLibrary.ownerOf(stickerId: String): StickerSet? = sets.firstOrNull { set -> set.stickers.any { it.id == stickerId } }

private fun StickerLibrary.withSet(set: StickerSet): StickerLibrary = copy(sets = sets.map { if (it.id == set.id) set else it })
