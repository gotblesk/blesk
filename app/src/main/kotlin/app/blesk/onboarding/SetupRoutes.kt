package app.blesk.onboarding

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.ChatSample
import app.blesk.feature.onboarding.CodeOfferScreen
import app.blesk.feature.onboarding.GlintStepScreen
import app.blesk.feature.onboarding.ProfileStepActions
import app.blesk.feature.onboarding.ProfileStepScreen
import app.blesk.feature.onboarding.ProfileStepState
import app.blesk.feature.onboarding.ThemeStepScreen
import app.blesk.feature.profile.AvatarCrop
import app.blesk.feature.settings.ThemePicker
import app.blesk.navigation.PhraseRoute
import app.blesk.navigation.PinChangeRoute
import app.blesk.navigation.rememberProfilePhoto
import app.blesk.shared.appearance.Appearance
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * One step of setting up the new account; [onNext] goes on to the next one, [onFinish] opens the chats
 * with the official channel to subscribe to, if any.
 */
@Composable
internal fun SetupRoute(container: AppContainer, key: SetupKey, onNext: () -> Unit, onFinish: (subscribeTo: String?) -> Unit) {
    when (key.step) {
        SetupStep.Profile -> ProfileStep(container, onNext)
        // Shown straight after the account is made, so no code is asked; «Позже» leaves the reminder in «Ты».
        SetupStep.Phrase -> PhraseRoute(container, fresh = true, onDone = onNext, onLater = onNext, onBack = null)
        SetupStep.Code -> CodeStep(container, onNext)
        SetupStep.Theme -> ThemeStep(container, onNext)
        SetupStep.Glint -> GlintStep(container.onboarding.officialChannel, onFinish)
    }
}

/** «Язык блеска», with the server's official channel offered ticked when the server has one. */
@Composable
internal fun GlintStep(officialChannel: Flow<String?>, onFinish: (subscribeTo: String?) -> Unit) {
    val channel by officialChannel.collectAsStateWithLifecycle(initialValue = null)
    var subscribe by rememberSaveable { mutableStateOf(true) }
    GlintStepScreen(
        channel = channel != null,
        subscribe = subscribe,
        onSubscribeChange = { subscribe = it },
        onDone = { onFinish(channel?.takeIf { subscribe }) },
    )
}

/** «Как тебя называть»: the name and the photo, as the demo left them if it did. */
@Composable
private fun ProfileStep(container: AppContainer, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val profile = container.settings.profile.collectAsStateWithLifecycle(initialValue = null).value ?: return
    val name = rememberTextFieldState(profile.name)
    val photo = rememberProfilePhoto(container, profile.photoVersion)
    // The name refused for showing nothing; the line under it goes once the name is changed.
    var refused by remember { mutableStateOf<String?>(null) }
    // A new photo is framed first; only the square under the circle is kept.
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = container.profilePhoto.decode(uri) }
    }
    val typed = name.text.toString()
    ProfileStepScreen(
        name = name,
        state = ProfileStepState(profile.username, photo, profile.photoVersion, refused = refused == typed),
        actions = ProfileStepActions(
            onPickPhoto = {
                container.lock.expectReturn()
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = {
                container.profilePhoto.clear()
                scope.launch { container.settings.bumpProfilePhoto() }
            },
            onNext = {
                scope.launch {
                    if (!container.onboarding.setProfileName(typed)) {
                        refused = typed
                        return@launch
                    }
                    if (container.settings.profile.first().name != profile.name) haptics.success()
                    onNext()
                }
            },
            onSkip = onNext,
        ),
        crop = cropping?.let { source ->
            {
                AvatarCrop(
                    source = source,
                    onDone = { square ->
                        // Closed first, so the step is back on screen for the photo's flash.
                        cropping = null
                        scope.launch { if (container.profilePhoto.save(square)) container.settings.bumpProfilePhoto() }
                    },
                    onCancel = { cropping = null },
                )
            }
        },
    )
}

/** The code's offer, and setting one on the spot; leaving the setting brings the offer back. */
@Composable
private fun CodeStep(container: AppContainer, onNext: () -> Unit) {
    var setting by rememberSaveable { mutableStateOf(false) }
    if (setting) {
        PinChangeRoute(container, requireCurrent = false, onDone = onNext, onCancel = { setting = false })
    } else {
        CodeOfferScreen(onEnable = { setting = true }, onLater = onNext)
    }
}

/** The theme on a piece of a chat; the whole app takes it at once. */
@Composable
private fun ThemeStep(container: AppContainer, onNext: () -> Unit) {
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    ThemeStepScreen(
        picker = {
            ThemePicker(
                current = appearance.theme,
                onSelect = { theme ->
                    if (theme == appearance.theme) return@ThemePicker
                    haptics.tick()
                    container.eventLog.record(EventCode.ThemeChanged, theme.preset.ordinal.toLong())
                    scope.launch { container.settings.updateAppearance { it.copy(theme = theme) } }
                },
            )
        },
        preview = { ChatSample() },
        onNext = onNext,
    )
}
