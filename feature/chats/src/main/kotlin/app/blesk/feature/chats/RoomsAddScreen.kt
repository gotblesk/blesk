package app.blesk.feature.chats

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.coroutines.launch

/** Create a room, or come in with an invite link. */
@Composable
fun RoomsAddScreen(onCreate: () -> Unit, onJoin: suspend (String) -> Boolean?, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val link = rememberTextFieldState()
    var outcome by remember { mutableStateOf<Boolean?>(null) }
    ScrollScreen(title = stringResource(R.string.rooms_add), onBack = onBack, modifier = modifier) {
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            ListRow(title = stringResource(R.string.rooms_create), icon = BleskIcons.Plus, onClick = onCreate)
        }
        SectionHeader(stringResource(R.string.rooms_join))
        TextInput(
            label = stringResource(R.string.rooms_join_field),
            state = link,
            hint = when (outcome) {
                true -> stringResource(R.string.rooms_join_sent)
                false -> stringResource(R.string.rooms_join_bad)
                null -> null
            },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        Button(
            text = stringResource(R.string.rooms_join),
            kind = ButtonKind.Secondary,
            onClick = { scope.launch { outcome = onJoin(link.text.toString().trim()) } },
            modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        )
    }
}
