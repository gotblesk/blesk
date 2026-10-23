package app.blesk.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.shared.protocol.TextSafety

/**
 * What the name step shows: the new account's [username], the chosen [photo] and its
 * [photoVersion], which grows each time a photo is set, and whether the typed name was [refused]
 * for having nothing visible in it.
 */
@Immutable
data class ProfileStepState(
    val username: String,
    val photo: ImageBitmap? = null,
    val photoVersion: Long = 0,
    val refused: Boolean = false,
)

@Stable
class ProfileStepActions(
    val onPickPhoto: () -> Unit,
    val onRemovePhoto: () -> Unit,
    val onNext: () -> Unit,
    val onSkip: () -> Unit,
)

/**
 * «Как тебя называть»: the name and the photo others see, both optional. The first step after
 * the account is made, so there is no way back from it. [crop] is drawn over the screen while a
 * picked photo is being framed.
 */
@Composable
fun ProfileStepScreen(
    name: TextFieldState,
    state: ProfileStepState,
    actions: ProfileStepActions,
    modifier: Modifier = Modifier,
    crop: (@Composable () -> Unit)? = null,
) {
    val haptics = rememberHaptics()
    LaunchedEffect(state.refused) { if (state.refused) haptics.refuse() }
    Box(modifier) {
        ScrollScreen(
            title = stringResource(R.string.onboarding_profile_title),
            bottom = {
                StepActions(
                    primary = StepButton(stringResource(R.string.onboarding_next), actions.onNext),
                    secondary = StepButton(stringResource(R.string.onboarding_skip), actions.onSkip),
                )
            },
        ) {
            StepText(stringResource(R.string.onboarding_profile_text))
            Photo(avatarName(name.text.toString(), state.username), state, actions.onPickPhoto)
            if (state.photo != null) {
                Button(
                    text = stringResource(R.string.onboarding_profile_remove),
                    onClick = actions.onRemovePhoto,
                    modifier = Modifier.fillMaxWidth().wrapContentWidth().padding(top = 8.dp),
                    kind = ButtonKind.Secondary,
                )
            }
            TextInput(
                label = stringResource(R.string.onboarding_profile_name),
                state = name,
                modifier = Modifier.padding(top = 12.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                inputTransformation = NameLength,
                onKeyboardAction = { actions.onNext() },
            )
            if (state.refused) {
                ProblemLine(
                    text = stringResource(R.string.onboarding_profile_invisible),
                    align = TextAlign.Start,
                    modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 4.dp),
                )
            }
        }
        crop?.invoke()
    }
}

/**
 * The avatar as others will see it, with a camera on its edge: a tap picks a photo. A new photo
 * sticks to it with a flash.
 */
@Composable
private fun Photo(shownName: String, state: ProfileStepState, onPick: () -> Unit) {
    val haptics = rememberHaptics()
    var seen by remember { mutableStateOf(state.photoVersion) }
    LaunchedEffect(state.photoVersion) {
        if (state.photoVersion == seen) return@LaunchedEffect
        seen = state.photoVersion
        haptics.stick()
    }
    val pick = stringResource(R.string.onboarding_profile_pick)
    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        Box {
            Avatar(
                name = shownName,
                size = AvatarSize,
                photo = state.photo,
                key = state.username,
                modifier = Modifier
                    .flashOn(state.photoVersion, CircleShape)
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onPick)
                    .semantics { contentDescription = pick },
            )
            Icon(
                icon = BleskIcons.Camera,
                contentDescription = null,
                tint = Blesk.colors.onAccent,
                size = 16.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(BadgeSize)
                    .clip(CircleShape)
                    .background(Blesk.colors.accent)
                    .padding(6.dp),
            )
        }
    }
}

/** Whose letter the avatar shows: the typed name once it has something to see, the username until then. */
internal fun avatarName(typed: String, username: String): String = TextSafety.clean(typed).takeIf(TextSafety::hasVisible) ?: username

/**
 * Keeps a name to [Profile.MAX_NAME] characters by cutting its end, so a long name pasted in is
 * shortened rather than refused whole. A character made of two halves is never cut in two.
 */
private val NameLength = InputTransformation {
    if (length <= Profile.MAX_NAME) return@InputTransformation
    val end = if (Character.isHighSurrogate(asCharSequence()[Profile.MAX_NAME - 1])) Profile.MAX_NAME - 1 else Profile.MAX_NAME
    replace(end, length, "")
}

private val AvatarSize = 96.dp
private val BadgeSize = 28.dp
