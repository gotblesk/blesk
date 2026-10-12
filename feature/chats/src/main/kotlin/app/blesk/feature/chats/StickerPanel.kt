package app.blesk.feature.chats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.data.stickers.Sticker
import app.blesk.core.data.stickers.StickerLibrary
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.media.StickerView

/** The stickers a chat can send, and what to do with them. */
class StickerShelf(
    val library: StickerLibrary,
    val onSend: (Sticker) -> Unit,
    val onToggleFavorite: (Sticker) -> Unit,
    /** «Ты → Стикеры», where sets are made. */
    val onOpenSets: () -> Unit,
)

private const val RECENT = "recent"
private const val FAVORITES = "favorites"

/** The «Стикеры» tab: search by emoji, recent, favourite, then every set. A long press marks a favourite. */
@Composable
internal fun StickerPanel(shelf: StickerShelf, modifier: Modifier = Modifier) {
    val library = shelf.library
    val query = rememberTextFieldState()
    val needle = query.text.toString().trim().lowercase()
    // The names of emoji are only needed for searching by word.
    val catalog by rememberEmojiCatalog(load = needle.isNotEmpty())
    var section by rememberSaveable { mutableStateOf(if (library.recent.isEmpty()) FAVORITES else RECENT) }
    val sections = buildList {
        if (library.recent.isNotEmpty()) add(RECENT to stringResource(R.string.emoji_recent))
        if (library.favorites.isNotEmpty()) add(FAVORITES to stringResource(R.string.sticker_favorites))
        library.sets.filter { it.stickers.isNotEmpty() }.forEach { add(it.id to it.title) }
    }
    val current = section.takeIf { id -> sections.any { it.first == id } } ?: sections.firstOrNull()?.first
    val shown = when {
        needle.isNotEmpty() -> search(library, catalog, needle)
        current == RECENT -> library.recentStickers
        current == FAVORITES -> library.favoriteStickers
        else -> library.sets.firstOrNull { it.id == current }?.stickers.orEmpty()
    }
    Column(modifier) {
        if (library.sets.none { it.stickers.isNotEmpty() }) {
            Empty(shelf.onOpenSets)
            return@Column
        }
        SearchField(query, stringResource(R.string.sticker_search))
        if (needle.isEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                items(sections, key = { it.first }) { (id, title) -> SectionChip(title, id == current) { section = id } }
                item { SectionChip(stringResource(R.string.sticker_sets_open), false, shelf.onOpenSets) }
            }
        }
        if (shown.isEmpty() && needle.isNotEmpty()) {
            Text(stringResource(R.string.sticker_nothing), color = Blesk.colors.textSecondary, modifier = Modifier.padding(16.dp))
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(StickerCell),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(shown, key = { it.id }) { sticker -> StickerCell(sticker, sticker.id in library.favorites, shelf) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StickerCell(sticker: Sticker, favorite: Boolean, shelf: StickerShelf) {
    val haptics = rememberHaptics()
    StickerView(
        path = sticker.path,
        format = sticker.format,
        description = sticker.caption.ifEmpty { sticker.emoji },
        plays = 1,
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.sticker_send),
                onLongClickLabel = stringResource(if (favorite) R.string.sticker_unfavorite else R.string.sticker_favorite),
                onLongClick = {
                    haptics.stick()
                    shelf.onToggleFavorite(sticker)
                },
                onClick = {
                    haptics.detach()
                    shelf.onSend(sticker)
                },
            ),
    )
}

@Composable
private fun Empty(onOpenSets: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.sticker_empty), color = Blesk.colors.textSecondary)
        Button(text = stringResource(R.string.sticker_create_set), kind = ButtonKind.Secondary, onClick = onOpenSets)
    }
}

/** By the emoji's Russian name or keywords, or by the sticker's own caption. */
private fun search(library: StickerLibrary, catalog: Map<EmojiSection, List<EmojiEntry>>, needle: String): List<Sticker> {
    val emoji = catalog.values.flatten().filter { it.matches(needle) }.map { it.emoji }.toSet()
    return library.sets.flatMap { it.stickers }.filter { sticker ->
        sticker.caption.lowercase().contains(needle) || emoji.any { sticker.emoji.contains(it) } || sticker.emoji.contains(needle)
    }
}

private val StickerCell = 76.dp
