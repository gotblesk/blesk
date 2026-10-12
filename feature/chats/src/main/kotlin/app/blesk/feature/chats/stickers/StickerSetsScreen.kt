package app.blesk.feature.chats.stickers

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.stickers.StickerLibrary
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.feature.chats.media.StickerView

/** «Ты → Стикеры»: every set on this phone, and a new one by its name. */
@Composable
fun StickerSetsScreen(
    library: StickerLibrary,
    onOpen: (setId: String) -> Unit,
    onCreate: (title: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    val name = rememberTextFieldState()
    val fallback = stringResource(R.string.stickers_default_title)
    ScrollScreen(title = stringResource(R.string.stickers_title), onBack = onBack, modifier = modifier) {
        if (library.sets.isNotEmpty()) {
            Card(pad) {
                library.sets.forEach { set ->
                    val count = set.stickers.size
                    ListRow(
                        title = set.title,
                        subtitle = if (set.bundled) {
                            stringResource(R.string.stickers_bundled, count)
                        } else {
                            pluralStringResource(R.plurals.stickers_count, count, count)
                        },
                        onClick = { onOpen(set.id) },
                        trailing = {
                            set.stickers.firstOrNull()?.let { first ->
                                StickerView(first.path, first.format, Modifier.size(40.dp), plays = 0)
                            }
                        },
                    )
                }
            }
        } else {
            Text(
                stringResource(R.string.stickers_none),
                color = Blesk.colors.textSecondary,
                modifier = pad.padding(vertical = 8.dp),
            )
        }
        SectionHeader(stringResource(R.string.stickers_new))
        TextInput(label = stringResource(R.string.stickers_new_name), state = name, hint = fallback)
        Box(pad.padding(top = 8.dp)) {
            Button(
                text = stringResource(R.string.sticker_create_set),
                kind = ButtonKind.Secondary,
                onClick = {
                    onCreate(name.text.toString().trim().ifEmpty { fallback })
                    name.clearText()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            stringResource(R.string.stickers_hint),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = pad.padding(vertical = Blesk.dimens.sectionGap),
        )
    }
}
