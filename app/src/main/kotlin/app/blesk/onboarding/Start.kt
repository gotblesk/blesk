package app.blesk.onboarding

import androidx.navigation3.runtime.NavKey
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.navigation.HomeKey

/**
 * The setup step after this one, in the brief's order: name and photo, the phrase, the code,
 * the theme, the glint. The code is not offered when one is [pinSet] already. Null after the last.
 */
internal fun SetupStep.next(pinSet: Boolean): SetupStep? = when (this) {
    SetupStep.Profile -> SetupStep.Phrase
    SetupStep.Phrase -> if (pinSet) SetupStep.Theme else SetupStep.Code
    SetupStep.Code -> SetupStep.Theme
    SetupStep.Theme -> SetupStep.Glint
    SetupStep.Glint -> null
}

/**
 * The screen the app opens with: the welcome screen until the demo is chosen or an account made, the step
 * an unfinished setup stopped at, otherwise the chats.
 */
internal fun startKey(start: StartState): NavKey = when (start) {
    StartState.Welcome -> WelcomeKey
    StartState.Demo -> HomeKey
    is StartState.Account -> start.pendingSetup?.let(::SetupKey) ?: HomeKey
}
