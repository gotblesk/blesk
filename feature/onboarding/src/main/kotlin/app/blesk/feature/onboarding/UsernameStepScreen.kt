package app.blesk.feature.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.UsernameInput
import app.blesk.core.design.component.UsernameNote
import app.blesk.core.design.component.UsernameStatus
import app.blesk.core.design.component.UsernameSuggestions
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** Whether the account is being created, and why it was not. */
@Immutable
data class UsernameStepState(val busy: Boolean = false, val problem: AccessProblem? = null)

/** [onTerms] and [onPolicy] open the rules and the privacy policy the terms line names. */
@Stable
class UsernameStepActions(
    val onCreate: () -> Unit,
    val onBack: () -> Unit,
    val onTerms: () -> Unit,
    val onPolicy: () -> Unit,
    val onUpdate: (() -> Unit)? = null,
)

/**
 * «Как тебя найдут»: the @username, checked as it is typed, and the honest word that there is no
 * phone number or email to win the account back by. «Создать аккаунт» does not go to the server
 * with a name already known not to work: it is refused here and the field gets the focus.
 */
@Composable
fun UsernameStepScreen(
    input: UsernameInput,
    status: UsernameStatus,
    state: UsernameStepState,
    actions: UsernameStepActions,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberHaptics()
    val field = remember { FocusRequester() }
    fun create() {
        if (state.busy) return
        if (status.blocksCreation()) {
            haptics.refuse()
            field.requestFocus()
        } else {
            actions.onCreate()
        }
    }
    ScrollScreen(
        title = stringResource(R.string.onboarding_username_title),
        onBack = actions.onBack,
        modifier = modifier,
        bottom = {
            StepActions(
                primary = StepButton(stringResource(R.string.onboarding_create), ::create, busy = state.busy),
                note = { Terms(actions.onTerms, actions.onPolicy) },
            )
        },
    ) {
        StepText(stringResource(R.string.onboarding_username_text))
        TextInput(
            label = stringResource(R.string.onboarding_username_label),
            state = input.field,
            modifier = Modifier.focusRequester(field),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Ascii,
                imeAction = ImeAction.Done,
            ),
            inputTransformation = input.transformation,
            prefix = "@",
            onKeyboardAction = { create() },
            supporting = {
                UsernameNote(status, input.notice, stringResource(R.string.onboarding_username_unknown))
                UsernameSuggestions(status, onPick = input::take)
            },
        )
        StatusLine(
            problem = state.problem,
            busy = state.busy,
            busyText = stringResource(R.string.onboarding_creating),
            onUpdate = actions.onUpdate,
        )
    }
}

/**
 * Whether the name is known not to work. One still being checked, or that could not be checked,
 * goes on: the server has the last word either way.
 */
internal fun UsernameStatus.blocksCreation(): Boolean = when (this) {
    UsernameStatus.Empty, is UsernameStatus.Problem, is UsernameStatus.Taken, is UsernameStatus.Reserved -> true
    UsernameStatus.Yours, UsernameStatus.Checking, is UsernameStatus.Free, UsernameStatus.Unknown -> false
}

/** «Создавая аккаунт, ты принимаешь правила и политику конфиденциальности», each a link. */
@Composable
private fun Terms(onTerms: () -> Unit, onPolicy: () -> Unit) {
    val template = stringResource(R.string.onboarding_terms)
    val rules = stringResource(R.string.onboarding_terms_rules)
    val policy = stringResource(R.string.onboarding_terms_policy)
    // Underlined besides being darker, so a link is told from the words around it without colour.
    val link = TextLinkStyles(SpanStyle(color = Blesk.colors.textPrimary, textDecoration = TextDecoration.Underline))
    val text = buildAnnotatedString {
        val first = template.indexOf(FIRST)
        val second = template.indexOf(SECOND)
        append(template.substring(0, first))
        withLink(LinkAnnotation.Clickable(RULES, link) { onTerms() }) { append(rules) }
        append(template.substring(first + FIRST.length, second))
        withLink(LinkAnnotation.Clickable(POLICY, link) { onPolicy() }) { append(policy) }
        append(template.substring(second + SECOND.length))
    }
    BasicText(
        text = text,
        style = Blesk.type.label.merge(color = Blesk.colors.textSecondary, textAlign = TextAlign.Center),
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val FIRST = "%1\$s"
private const val SECOND = "%2\$s"
private const val RULES = "rules"
private const val POLICY = "policy"
