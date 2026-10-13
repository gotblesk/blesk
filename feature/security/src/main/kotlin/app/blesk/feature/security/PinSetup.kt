package app.blesk.feature.security

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A new code in two steps: «Придумай код» (4 digits, 6 digits or a password), then «Повтори код».
 * «Отмена» at the top left and the back gesture lead out: from the second step back to the first,
 * from the first out without saving. [onSave] stretches the code off the main thread; if that
 * takes longer than 300 ms, «Сохраняю…» says so. Matching codes end with the success buzz, codes
 * that differ with the refusing one, «Коды не совпали» and the first step again.
 */
@Composable
fun PinSetup(
    onSave: suspend (String) -> Unit,
    onDone: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    minPassword: Int = 6,
) {
    // 4, 6, or 0 for a password typed on the keyboard.
    var length by rememberSaveable { mutableIntStateOf(4) }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    var saving by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    val options = persistentListOf(
        stringResource(R.string.pin_digits_4),
        stringResource(R.string.pin_digits_6),
        stringResource(R.string.pin_password),
    )
    val pending = first
    fun back() {
        if (saving) return
        if (pending != null) {
            first = null
            problem = null
        } else {
            onCancel()
        }
    }
    BackHandler(enabled = !saving) { back() }
    Box(modifier.fillMaxSize()) {
        PinEntry(
            title = stringResource(if (pending == null) R.string.pin_new_title else R.string.pin_repeat_title),
            length = length,
            message = when (problem) {
                null -> if (length == 0 && pending == null) stringResource(R.string.pin_password_hint, minPassword) else null
                else -> stringResource(problem ?: R.string.pin_mismatch)
            },
            check = { code ->
                when {
                    length == 0 && code.length < minPassword -> {
                        problem = R.string.pin_password_short
                        PinAttempt.Wrong(0)
                    }
                    pending == null -> {
                        first = code
                        problem = null
                        PinAttempt.Ok
                    }
                    pending == code -> {
                        coroutineScope {
                            val slow = launch {
                                delay(SAVING_AFTER_MILLIS)
                                saving = true
                            }
                            onSave(code)
                            slow.cancel()
                        }
                        saving = false
                        haptics.success()
                        onDone()
                        PinAttempt.Ok
                    }
                    else -> {
                        first = null
                        problem = R.string.pin_mismatch
                        PinAttempt.Wrong(0)
                    }
                }
            },
            header = {
                if (pending == null) {
                    Segmented(
                        options = options,
                        selected = when (length) {
                            4 -> 0
                            6 -> 1
                            else -> 2
                        },
                        onSelect = { length = listOf(4, 6, 0)[it] },
                        modifier = Modifier.padding(bottom = 32.dp),
                    )
                }
            },
        )
        Button(
            text = stringResource(R.string.pin_cancel),
            onClick = ::back,
            kind = ButtonKind.Secondary,
            modifier = Modifier.align(Alignment.TopStart).windowInsetsPadding(WindowInsets.safeDrawing).padding(12.dp),
        )
        AnimatedVisibility(
            visible = saving,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.pin_saving),
                style = Blesk.type.bodyStrong,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Blesk.colors.surfaceStrong)
                    .semantics { liveRegion = LiveRegionMode.Polite }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            )
        }
    }
}

private const val SAVING_AFTER_MILLIS = 300L

/** After a new code: whether to open with the fingerprint too; the code stays for when it fails. */
@Composable
fun BiometricOffer(onEnable: () -> Unit, onSkip: () -> Unit, modifier: Modifier = Modifier) {
    BackHandler(onBack = onSkip)
    Column(
        modifier
            .fillMaxSize()
            .background(Blesk.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(BleskIcons.Fingerprint, contentDescription = null, size = 48.dp)
        Text(
            text = stringResource(R.string.pin_biometric_offer),
            style = Blesk.type.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.pin_biometric_offer_hint),
            color = Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        Button(stringResource(R.string.pin_biometric_on), onClick = onEnable, icon = BleskIcons.Fingerprint)
        Button(stringResource(R.string.pin_biometric_later), onClick = onSkip, kind = ButtonKind.Secondary)
    }
}
