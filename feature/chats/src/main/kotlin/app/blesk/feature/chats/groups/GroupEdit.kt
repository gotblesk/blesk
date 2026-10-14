package app.blesk.feature.chats.groups

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R
import app.blesk.shared.protocol.GroupChange
import app.blesk.shared.protocol.GroupPolicy
import app.blesk.shared.protocol.GroupState

/** What is being typed into the details while editing, before «Сохранить». */
@Stable
internal class EditDraft(val title: TextFieldState, val description: TextFieldState, hue: Int?) {
    var hue by mutableStateOf(hue)

    fun dirty(state: GroupState) =
        title.text.toString().trim() != state.title || description.text.toString().trim() != state.description || hue != state.hue

    fun toChange() = GroupChange.Describe(
        title = title.text.toString().take(GroupPolicy.MAX_TITLE),
        description = description.text.toString().take(GroupPolicy.MAX_DESCRIPTION),
        hue = hue,
    )
}

@Composable
internal fun rememberEditDraft(state: GroupState): EditDraft {
    val title = rememberTextFieldState(state.title)
    val description = rememberTextFieldState(state.description)
    return remember(state.id) { EditDraft(title, description, state.hue) }
}

/** The details made editable where they stand: name, description and the community's hue. */
@Composable
internal fun EditHero(draft: EditDraft, hint: String?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp)) {
        if (hint != null) {
            Text(
                hint,
                style = Blesk.type.bodyStrong,
                color = Blesk.colors.textSecondary,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 8.dp),
            )
        }
        TextInput(label = stringResource(R.string.manage_edit_title), state = draft.title)
        TextInput(label = stringResource(R.string.manage_edit_description), state = draft.description, singleLine = false)
        Text(
            stringResource(R.string.manage_edit_hue),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(top = 6.dp),
        )
        HuePicker(selected = draft.hue, onSelect = { draft.hue = it }, allowLight = true)
    }
}

/** «Сохранить» flowing up from the bottom once something changed. */
@Composable
internal fun SaveCapsule(visible: Boolean, dirty: Boolean, onSave: () -> Unit) {
    AnimatedVisibility(
        visible = visible && dirty,
        enter = slideInVertically(Blesk.motion.settle()) { it } + fadeIn(),
        exit = slideOutVertically(Blesk.motion.settle()) { it } + fadeOut(),
    ) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Blesk.dimens.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Button(stringResource(R.string.group_save), onClick = onSave, icon = BleskIcons.Check)
        }
    }
}
