package app.blesk.navigation

import android.graphics.Rect
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.media.PreparedSticker
import app.blesk.core.media.StickerMaker
import app.blesk.feature.chats.R
import app.blesk.feature.chats.stickers.MadeSticker
import app.blesk.feature.chats.stickers.StickerMakerScreen
import app.blesk.feature.chats.stickers.StickerSetActions
import app.blesk.feature.chats.stickers.StickerSetScreen
import app.blesk.feature.chats.stickers.StickerSetsScreen
import app.blesk.feature.chats.stickers.StickerSource
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data object StickersKey : NavKey

@Serializable
data class StickerSetKey(val setId: String) : NavKey

/** A new sticker from [uri], into [setId] or, when null, a set of its own. */
@Serializable
data class StickerMakeKey(val uri: String, val setId: String? = null) : NavKey

@Composable
internal fun StickersRoute(container: AppContainer, onOpen: (String) -> Unit, onBack: () -> Unit) {
    val library by container.stickers.library.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    StickerSetsScreen(
        library = library,
        onOpen = onOpen,
        onCreate = { title -> scope.launch { onOpen(container.stickers.createSet(title).id) } },
        onBack = onBack,
    )
}

@Composable
internal fun StickerSetRoute(container: AppContainer, setId: String, onMake: (StickerMakeKey) -> Unit, onBack: () -> Unit) {
    val library by container.stickers.library.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val refused = stringResource(R.string.stickers_lottie_refused)
    val pictures = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onMake(StickerMakeKey(uri.toString(), setId))
    }
    val animation = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Checked here, so a file that will not do is refused before the maker opens.
        scope.launch {
            val probe = container.stickerMaker.importAsIs(uri)
            probe?.file?.delete()
            if (probe == null) notify(refused) else onMake(StickerMakeKey(uri.toString(), setId))
        }
    }
    val set = library.sets.firstOrNull { it.id == setId } ?: return
    StickerSetScreen(
        set = set,
        actions = StickerSetActions(
            onRename = { title -> scope.launch { container.stickers.renameSet(setId, title) } },
            onAddPictures = { pictures.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAddAnimation = { animation.launch(arrayOf("application/json")) },
            onEdit = { sticker, emoji, caption -> scope.launch { container.stickers.edit(sticker.id, emoji, caption) } },
            onRemove = { sticker -> scope.launch { container.stickers.remove(sticker.id) } },
            onDeleteSet = {
                scope.launch {
                    container.stickers.deleteSet(setId)
                    onBack()
                }
            },
            onBack = onBack,
        ),
    )
}

/** An animated file goes in as it is; anything else is decoded for framing. */
@Composable
internal fun StickerMakeRoute(container: AppContainer, key: StickerMakeKey, onBack: () -> Unit) {
    val library by container.stickers.library.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    val unreadable = stringResource(R.string.stickers_picture_failed)
    val full = stringResource(R.string.stickers_full)
    val fallbackTitle = stringResource(R.string.stickers_default_title)
    val added = stringResource(R.string.sticker_added, "%s")
    val source by produceState<StickerSource?>(null, key.uri) {
        val uri = Uri.parse(key.uri)
        value = container.stickerMaker.importAsIs(uri)?.let { StickerSource.Ready(it.file.path, it.format) }
            ?: container.stickerMaker.decode(uri)?.let { StickerSource.Picture(it) }
        if (value == null) {
            notify(unreadable)
            onBack()
        }
    }
    val ready = source ?: return
    val own = library.sets.filter { !it.bundled && !it.full }
    StickerMakerScreen(
        source = ready,
        sets = own,
        initialSetId = key.setId?.takeIf { id -> own.any { it.id == id } },
        onDone = { made ->
            scope.launch {
                val prepared = prepare(container, ready, made) ?: return@launch notify(unreadable)
                val setId = made.setId ?: container.stickers.createSet(fallbackTitle).id
                val sticker = container.stickers.add(setId, prepared.file, made.emoji, made.caption, prepared.format)
                if (sticker == null) {
                    prepared.file.delete()
                    notify(full)
                    return@launch
                }
                val title = container.stickers.library.value.sets.firstOrNull { it.id == setId }?.title.orEmpty()
                notify(added.format(title))
                onBack()
            }
        },
        onBack = onBack,
    )
}

private suspend fun prepare(container: AppContainer, source: StickerSource, made: MadeSticker): PreparedSticker? = when (source) {
    is StickerSource.Ready -> PreparedSticker(File(source.path), source.format)
    is StickerSource.Picture -> {
        val bitmap = source.bitmap
        val crop = made.crop ?: Rect(0, 0, bitmap.width, bitmap.height)
        container.stickerMaker.encode(StickerMaker.render(bitmap, crop, made.outline))
    }
}
