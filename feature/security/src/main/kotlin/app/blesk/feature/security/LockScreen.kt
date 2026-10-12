package app.blesk.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What checking a code led to. */
sealed interface PinAttempt {
    data object Ok : PinAttempt
    data class Wrong(val waitMillis: Long) : PinAttempt
}

/**
 * Asks for the code. [check] is called once [length] digits are in; a wrong code shakes the
 * dots and, after several mistakes, the keypad waits before the next try.
 */
@Composable
fun PinEntry(
    title: String,
    length: Int,
    check: (String) -> PinAttempt,
    modifier: Modifier = Modifier,
    message: String? = null,
    onBiometric: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val shake = remember { Animatable(0f) }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val wrong = stringResource(R.string.lock_wrong)
    LaunchedEffect(waitUntil) {
        while (System.currentTimeMillis() < waitUntil) {
            now = System.currentTimeMillis()
            delay(TICK_MILLIS)
        }
        now = System.currentTimeMillis()
    }
    val waiting = waitUntil > now
    val waitText = if (waiting) stringResource(R.string.lock_wait, formatWait(waitUntil - now)) else null

    fun submit(code: String) {
        when (val result = check(code)) {
            PinAttempt.Ok -> Unit
            is PinAttempt.Wrong -> {
                haptics.refuse()
                error = wrong
                waitUntil = System.currentTimeMillis() + result.waitMillis
                scope.launch {
                    shake.animateTo(0f, keyframes {
                        durationMillis = SHAKE_MILLIS
                        -24f at 60
                        20f at 140
                        -12f at 220
                        6f at 300
                    })
                    entered = ""
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Blesk.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        header()
        Text(title, style = Blesk.type.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(28.dp))
        PinDots(length = length, entered = entered.length, shake = shake)
        Text(
            text = waitText ?: error ?: message ?: " ",
            style = Blesk.type.label,
            color = if (error != null || waiting) Blesk.colors.danger else Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, bottom = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        Keypad(
            onDigit = { digit ->
                if (waiting || entered.length >= length) return@Keypad
                error = null
                entered += digit
                if (entered.length == length) submit(entered)
            },
            onDelete = { entered = entered.dropLast(1) },
            onBiometric = onBiometric,
        )
    }
}

private const val TICK_MILLIS = 500L
private const val SHAKE_MILLIS = 360

@Composable
private fun formatWait(millis: Long): String {
    val seconds = ((millis + 999) / 1000).toInt()
    return if (seconds < 60) {
        stringResource(R.string.lock_seconds, seconds)
    } else {
        stringResource(R.string.lock_minutes, (seconds + 59) / 60)
    }
}
