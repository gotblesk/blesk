package app.blesk.navigation

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.crypto.Biometrics
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.BlockedScreen
import app.blesk.feature.chats.groups.IdentityGate
import app.blesk.feature.security.BiometricOffer
import app.blesk.feature.security.PinAttempt
import app.blesk.feature.security.PinEntry
import app.blesk.feature.security.PinSetup
import app.blesk.feature.security.PrivacyActions
import app.blesk.feature.security.PrivacyScreen
import app.blesk.feature.security.WipeKind
import app.blesk.feature.security.WipeScreen
import app.blesk.locks.applyPendingLock
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import app.blesk.feature.security.R as SecurityR

internal suspend fun PinVault.attempt(pin: String): PinAttempt = check(pin).toAttempt()

@Composable
internal fun PrivacyRoute(
    container: AppContainer,
    onSetPin: () -> Unit,
    onChangePin: () -> Unit,
    onDisablePin: () -> Unit,
    onWipe: () -> Unit,
    onBack: () -> Unit,
    onOpenBlocked: () -> Unit = {},
    onOpenPhrase: () -> Unit = {},
    onOpenLocks: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    PrivacyScreen(
        privacy = privacy,
        pinSet = container.pinVault.isSet,
        biometricAvailable = remember { Biometrics.available(context) },
        actions = PrivacyActions(
            onChange = { next ->
                scope.launch {
                    container.settings.updatePrivacy { next }
                    if (next.findable != privacy.findable) container.chats.value?.setDiscoverable(next.findable)
                }
            },
            onEnablePin = onSetPin,
            onDisablePin = onDisablePin,
            onChangePin = onChangePin,
            onWipe = onWipe,
            onBack = onBack,
            onOpenBlocked = onOpenBlocked,
            onOpenPhrase = onOpenPhrase.takeIf { container.phrase.exists },
            onOpenLocks = onOpenLocks,
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
        val scope = rememberCoroutineScope()
        val context = LocalContext.current
        var offerBiometric by rememberSaveable { mutableStateOf(false) }
        if (offerBiometric) {
            BiometricOffer(
                onEnable = {
                    scope.launch {
                        container.settings.updatePrivacy { it.copy(biometric = true) }
                        onDone()
                    }
                },
                onSkip = onDone,
            )
            return
        }
        PinSetup(
            minPassword = PinVault.MIN_PASSWORD,
            onSave = { pin ->
                container.pinVault.set(pin)
                container.applyPendingLock()
            },
            onDone = {
                scope.launch {
                    val biometric = container.settings.privacy.first().biometric
                    if (!biometric && Biometrics.available(context)) offerBiometric = true else onDone()
                }
            },
            onCancel = onDone,
        )
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

/**
 * The lock shown over everything until the right code or a strong biometric. «Забыл код» leads
 * to wiping the phone, the only way past a code that is stored nowhere.
 */
@Composable
internal fun LockRoute(container: AppContainer, biometric: Boolean, wipeAfterTen: Boolean) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val title = stringResource(SecurityR.string.lock_biometric_prompt)
    val cancel = stringResource(SecurityR.string.lock_biometric_cancel)
    var forgot by remember { mutableStateOf(false) }
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
    // No way back past the lock: «назад» leaves blesk instead of reaching the screens under it.
    BackHandler(enabled = !forgot) { activity?.moveTaskToBack(true) }
    if (forgot) {
        BackHandler { forgot = false }
        WipeScreen(kind = WipeKind.ForgotCode, onWipe = { wipeEverything(context, container) }, onBack = { forgot = false })
        return
    }
    PinEntry(
        title = stringResource(SecurityR.string.lock_title),
        length = container.pinVault.length,
        lock = true,
        check = { pin ->
            val result = container.pinVault.check(pin)
            if (wipeAfterTen && result is PinVault.Result.Wrong && result.failures >= WIPE_AT) wipeEverything(context, container)
            result.toAttempt()
        },
        onOpened = container.lock::unlock,
        onBiometric = if (canUseBiometric) promptBiometric else null,
        onForgot = { forgot = true },
    )
}

private const val WIPE_AT = 10

internal fun PinVault.Result.toAttempt(): PinAttempt = when (this) {
    PinVault.Result.Ok -> PinAttempt.Ok
    is PinVault.Result.Wrong -> PinAttempt.Wrong(waitMillis)
    is PinVault.Result.Throttled -> PinAttempt.Wrong(waitMillis)
}

/** Erases everything the app has on this phone and closes it, as if just installed. */
internal fun wipeEverything(context: Context, container: AppContainer) {
    container.pinVault.clear()
    context.getSystemService(ActivityManager::class.java).clearApplicationUserData()
}

@Composable
internal fun BlockedRoute(container: AppContainer, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val blocked by remember(chats) { chats.blocked }.collectAsStateWithLifecycle(initialValue = emptyList())
    BlockedScreen(people = blocked.toImmutableList(), onUnblock = { chats.setBlocked(it.username, false) }, onBack = onBack)
}

/**
 * Proof that it is the owner of the phone before something that cannot be undone: the app's
 * code, or a fingerprint when it is allowed. Null when no code is set, as then there is nothing
 * to ask for. Wrong codes count against the same limit as on the lock screen.
 */
@Composable
internal fun rememberIdentityGate(container: AppContainer): IdentityGate? {
    if (!container.pinVault.isSet) return null
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    val biometric = privacy.biometric
    return remember(biometric) {
        { onPassed, onCancel ->
            val activity = LocalActivity.current
            val context = LocalContext.current
            val title = stringResource(SecurityR.string.lock_biometric_prompt)
            val cancel = stringResource(SecurityR.string.lock_biometric_cancel)
            val canUseBiometric = biometric && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Biometrics.available(context)
            BackHandler(onBack = onCancel)
            Box(Modifier.fillMaxSize().background(Blesk.colors.background).blockTaps()) {
                PinEntry(
                    title = stringResource(app.blesk.feature.chats.R.string.manage_confirm_title),
                    length = container.pinVault.length,
                    check = { pin -> container.pinVault.check(pin).toAttempt() },
                    onOpened = onPassed,
                    onBiometric = if (canUseBiometric && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        { Biometrics.prompt(activity, title, cancel, onPassed) }
                    } else {
                        null
                    },
                )
            }
        }
    }
}
