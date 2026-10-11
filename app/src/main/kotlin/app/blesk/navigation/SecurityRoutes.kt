package app.blesk.navigation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.crypto.Biometrics
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.settings.Privacy
import app.blesk.feature.security.PinAttempt
import app.blesk.feature.security.PinEntry
import app.blesk.feature.security.PinSetup
import app.blesk.feature.security.PrivacyActions
import app.blesk.feature.security.PrivacyScreen
import kotlinx.coroutines.launch
import app.blesk.feature.security.R as SecurityR

internal fun PinVault.attempt(pin: String): PinAttempt = when (val result = check(pin)) {
    PinVault.Result.Ok -> PinAttempt.Ok
    is PinVault.Result.Wrong -> PinAttempt.Wrong(result.waitMillis)
    is PinVault.Result.Throttled -> PinAttempt.Wrong(result.waitMillis)
}

@Composable
internal fun PrivacyRoute(
    container: AppContainer,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onDisablePin: () -> Unit,
    onWipe: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    PrivacyScreen(
        privacy = privacy,
        pinSet = container.pinVault.isSet,
        biometricAvailable = remember { Biometrics.available(context) },
        actions = PrivacyActions(
            onChange = { next -> scope.launch { container.settings.updatePrivacy { next } } },
            onEnablePin = onSetPin,
            onDisablePin = onDisablePin,
            onChangePin = onChangePin,
            onWipe = onWipe,
            onBack = onBack,
        ),
    )
}

/** Set a new code; when [requireCurrent], the current code is asked first. */
@Composable
internal fun PinChangeRoute(container: AppContainer, requireCurrent: Boolean, onDone: () -> Unit) {
    var verified by rememberSaveable { mutableStateOf(!requireCurrent) }
    if (!verified) {
        PinEntry(
            title = stringResource(SecurityR.string.pin_current_title),
            length = container.pinVault.length,
            check = { pin -> container.pinVault.attempt(pin).also { if (it == PinAttempt.Ok) verified = true } },
        )
    } else {
        PinSetup(onDone = { pin ->
            container.pinVault.set(pin)
            onDone()
        })
    }
}

/** Turning the lock off needs the current code. */
@Composable
internal fun PinDisableRoute(container: AppContainer, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    PinEntry(
        title = stringResource(SecurityR.string.pin_current_title),
        length = container.pinVault.length,
        check = { pin ->
            container.pinVault.attempt(pin).also {
                if (it == PinAttempt.Ok) {
                    container.pinVault.clear()
                    scope.launch { container.settings.updatePrivacy { privacy -> privacy.copy(biometric = false) } }
                    onDone()
                }
            }
        },
    )
}

/** The lock shown over everything until the right code or a strong biometric. */
@Composable
internal fun LockRoute(container: AppContainer, biometric: Boolean) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val title = stringResource(SecurityR.string.lock_biometric_prompt)
    val cancel = stringResource(SecurityR.string.lock_biometric_cancel)
    val canUseBiometric = biometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Biometrics.available(context)
    val promptBiometric: () -> Unit = {
        if (canUseBiometric && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Biometrics.prompt(activity, title, cancel) { container.lock.unlock() }
        }
    }
    DisposableEffect(Unit) {
        promptBiometric()
        onDispose { }
    }
    PinEntry(
        title = stringResource(SecurityR.string.lock_title),
        length = container.pinVault.length,
        check = { pin -> container.pinVault.attempt(pin).also { if (it == PinAttempt.Ok) container.lock.unlock() } },
        onBiometric = if (canUseBiometric) promptBiometric else null,
    )
}

/** Erases everything the app has on this phone and closes it, as if just installed. */
internal fun wipeEverything(context: Context, container: AppContainer) {
    container.pinVault.clear()
    context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
}
