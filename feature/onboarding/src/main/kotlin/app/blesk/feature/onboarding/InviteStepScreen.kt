package app.blesk.feature.onboarding

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.theme.Blesk
import app.blesk.shared.protocol.Invites
import kotlinx.coroutines.launch

/**
 * What the invite step shows: whether the server lets people in only by invite, and why it
 * refused the code last time.
 */
@Immutable
data class InviteStepState(val required: Boolean = true, val problem: AccessProblem? = null)

/** [onNext] gets the code, or null when the field was left empty on a server that needs none. */
@Stable
class InviteStepActions(val onNext: (code: String?) -> Unit, val onBack: () -> Unit)

/**
 * «Приглашение»: the invite code, typed or pasted, a whole link or a message with one. A code
 * that cannot be one, or none where one is needed, is refused here, before the server is asked.
 */
@Composable
fun InviteStepScreen(code: TextFieldState, state: InviteStepState, actions: InviteStepActions, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val typed = code.text.toString()
    // A refusal stands while the code it was about does; the next letter typed clears it.
    var refused by remember { mutableStateOf<Pair<String, AccessProblem>?>(null) }
    val shown = refused?.takeIf { it.first == typed }?.second ?: state.problem
    fun next() {
        val problem = when {
            typed.isBlank() && state.required -> AccessProblem.InviteRequired
            typed.isNotBlank() && !Invites.isValid(typed) -> AccessProblem.InviteInvalid
            else -> null
        }
        if (problem != null) refused = typed to problem else actions.onNext(typed.takeIf { it.isNotBlank() })
    }
    ScrollScreen(
        title = stringResource(R.string.onboarding_invite_title),
        onBack = actions.onBack,
        modifier = modifier,
        bottom = { StepActions(StepButton(stringResource(R.string.onboarding_next), ::next)) },
    ) {
        StepText(stringResource(R.string.onboarding_invite_text))
        TextInput(
            label = stringResource(R.string.onboarding_invite_code),
            state = code,
            hint = stringResource(R.string.onboarding_invite_hint),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
            ),
            inputTransformation = InviteText,
            onKeyboardAction = { next() },
            supporting = if (state.required) {
                null
            } else {
                { Text(stringResource(R.string.onboarding_invite_optional), style = Blesk.type.label, color = Blesk.colors.textSecondary) }
            },
        )
        StatusLine(problem = shown)
        Button(
            text = stringResource(R.string.onboarding_invite_paste),
            onClick = {
                // Read only on this tap: the clipboard is the person's, not something to watch.
                scope.launch {
                    val pasted = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)
                    pasted?.let { code.setTextAndPlaceCursorAtEnd(inviteIn(it.toString())) }
                }
            },
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp),
            kind = ButtonKind.Secondary,
        )
    }
}

/** The invite in [text]: the code of a link or a message with one, or else what was typed, in small letters. */
internal fun inviteIn(text: String): String = Invites.find(text) ?: text.lowercase()

/** Keeps the field to what the server will check, however the code came in. */
private val InviteText = InputTransformation {
    val typed = asCharSequence().toString()
    val invite = inviteIn(typed)
    // Replacing the text ends the keyboard's composition, so text that is already right is left alone.
    if (invite == typed) return@InputTransformation
    val sameLength = invite.length == typed.length
    val cursor = selection
    replace(0, length, invite)
    selection = if (sameLength) cursor else TextRange(length)
}
