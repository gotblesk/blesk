package app.blesk.core.data.stickers

import app.blesk.core.data.model.StickerFormat
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** One sticker of a set. [file] is its name inside the set's folder; [path] is filled in on loading. */
@Serializable
data class Sticker(
    val id: String,
    val file: String,
    val emoji: String,
    val caption: String = "",
    val format: StickerFormat = StickerFormat.Static,
    @Transient val path: String = "",
)

/** A set of stickers. Bundled sets come with the app, drawn by people, and cannot be changed. */
@Serializable
data class StickerSet(
    val id: String,
    val title: String,
    val stickers: List<Sticker> = emptyList(),
    val bundled: Boolean = false,
) {
    val full: Boolean get() = stickers.size >= MAX_STICKERS

    companion object {
        const val MAX_STICKERS = 120
    }
}

/** Everything sticker-related on this phone: the sets, and which stickers are favourite or recent. */
@Serializable
data class StickerLibrary(
    val sets: List<StickerSet> = emptyList(),
    val favorites: List<String> = emptyList(),
    val recent: List<String> = emptyList(),
) {
    private val byId: Map<String, Sticker> by lazy { sets.flatMap { it.stickers }.associateBy { it.id } }

    fun find(id: String): Sticker? = byId[id]

    val favoriteStickers: List<Sticker> get() = favorites.mapNotNull(byId::get)
    val recentStickers: List<Sticker> get() = recent.mapNotNull(byId::get)

    /** Stickers standing for [emoji], favourites first, for search and for suggestions over the field. */
    fun matching(emoji: String): List<Sticker> {
        val wanted = emoji.withoutVariation()
        if (wanted.isEmpty()) return emptyList()
        val found = sets.flatMap { it.stickers }.filter { it.emoji.withoutVariation().contains(wanted) }
        return found.sortedByDescending { it.id in favorites }
    }

    companion object {
        const val MAX_RECENT = 30
    }
}

/** The emoji variation selector comes and goes between keyboards; matching ignores it. */
private fun String.withoutVariation() = replace("️", "").trim()
