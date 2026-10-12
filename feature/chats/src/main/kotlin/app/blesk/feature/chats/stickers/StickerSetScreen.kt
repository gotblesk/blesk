package app.blesk.feature.chats.stickers

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.data.stickers.Sticker
import app.blesk.core.data.stickers.StickerSet
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.feature.chats.media.StickerView

class StickerSetActions(
    val onRename: (title: String) -> Unit,
    val onAddPictures: () -> Unit,
    val onAddAnimation: () -> Unit,
    val onEdit: (sticker: Sticker, emoji: String, caption: String) -> Unit,
    val onRemove: (Sticker) -> Unit,
    val onDeleteSet: () -> Unit,
    val onBack: () -> Unit,
)

/**
 * One set: its stickers in a grid. In a set of your own a tap on a sticker opens its emoji and
 * caption for editing, and new ones come from the gallery or as Lottie files.
 */
@Composable
fun StickerSetScreen(set: StickerSet, actions: StickerSetActions, modifier: Modifier = Modifier) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val editable = !set.bundled
    ScrollScreen(title = set.title, onBack = actions.onBack, modifier = modifier) {
        if (editable) {
            val title = rememberTextFieldState(set.title)
            TextInput(label = stringResource(R.string.stickers_set_name), state = title)
            LaunchedEffect(title.text) {
                val next = title.text.toString().trim()
                if (next.isNotEmpty() && next != set.title) actions.onRename(next)
            }
        }
        SectionHeader(stringResource(R.string.stickers_in_set, set.stickers.size, StickerSet.MAX_STICKERS))
        FlowRow(
            pad,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            set.stickers.forEach { sticker ->
                val selected = sticker.id == picked
                StickerView(
                    path = sticker.path,
                    format = sticker.format,
                    description = sticker.caption.ifEmpty { sticker.emoji },
                    plays = 1,
                    modifier = Modifier
                        .size(Cell)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selected) Blesk.colors.surfaceStrong else Blesk.colors.surface)
                        .let { if (selected) it.border(2.dp, Blesk.colors.accent, RoundedCornerShape(14.dp)) else it }
                        .semantics { this.selected = selected }
                        .let { cell ->
                            if (editable) cell.clickable(role = Role.Button) { picked = if (selected) null else sticker.id } else cell
                        }
                        .padding(6.dp),
                )
            }
            if (editable && !set.full) AddTile(actions.onAddPictures)
        }
        set.stickers.firstOrNull { it.id == picked }?.let { sticker ->
            StickerEditor(sticker, actions) { picked = null }
        }
        if (editable) {
            if (!set.full) {
                Row(pad.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        text = stringResource(R.string.stickers_add_pictures),
                        onClick = actions.onAddPictures,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        text = stringResource(R.string.stickers_add_lottie),
                        kind = ButtonKind.Secondary,
                        onClick = actions.onAddAnimation,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            DeleteSet(actions.onDeleteSet)
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val label = stringResource(R.string.stickers_add_pictures)
    Box(
        Modifier
            .size(Cell)
            .clip(RoundedCornerShape(14.dp))
            .background(Blesk.colors.surface)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(BleskIcons.Plus, contentDescription = null, tint = Blesk.colors.textSecondary)
    }
}

@Composable
private fun StickerEditor(sticker: Sticker, actions: StickerSetActions, onClose: () -> Unit) {
    val emoji = rememberTextFieldState(sticker.emoji)
    val caption = rememberTextFieldState(sticker.caption)
    LaunchedEffect(sticker.id) {
        emoji.setTextAndPlaceCursorAtEnd(sticker.emoji)
        caption.setTextAndPlaceCursorAtEnd(sticker.caption)
    }
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 16.dp)) {
        TextInput(label = stringResource(R.string.sticker_emoji), state = emoji, hint = stringResource(R.string.sticker_emoji_hint))
        TextInput(label = stringResource(R.string.sticker_caption), state = caption, hint = stringResource(R.string.sticker_caption_hint))
        Row(
            Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                text = stringResource(R.string.sticker_remove),
                kind = ButtonKind.Secondary,
                onClick = {
                    actions.onRemove(sticker)
                    onClose()
                },
                modifier = Modifier.weight(1f),
            )
            Button(
                text = stringResource(R.string.sticker_save),
                onClick = {
                    actions.onEdit(sticker, emoji.text.toString(), caption.text.toString())
                    onClose()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A second, deliberate tap: a set and its stickers go for good. */
@Composable
private fun DeleteSet(onDelete: () -> Unit) {
    var armed by rememberSaveable { mutableStateOf(false) }
    Button(
        text = stringResource(if (armed) R.string.stickers_delete_confirm else R.string.stickers_delete),
        kind = ButtonKind.Secondary,
        onClick = { if (armed) onDelete() else armed = true },
        modifier = Modifier.padding(Blesk.dimens.gutter).padding(top = 16.dp).fillMaxWidth(),
    )
    if (armed) {
        Text(
            stringResource(R.string.stickers_delete_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter),
        )
    }
}

private val Cell = 76.dp
