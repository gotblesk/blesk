package app.blesk.onboarding

import app.blesk.core.data.settings.SetupStep

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
