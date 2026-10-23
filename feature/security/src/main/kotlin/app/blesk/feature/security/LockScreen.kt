package app.blesk.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.SecretInput
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.formatWait
import app.blesk.core.design.icon.BleskIcons
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
 * Asks for the code: [length] digits on the keypad, or a password when [length] is 0. [check]
 * runs off the main thread (the code is stretched slowly on purpose). A wrong code dims the
 * slots and gives the refusing buzz, without shaking; after several mistakes the entry waits.
 * When [lock] is set the closed lock above the title comes away upwards on the right code,
 * and [onOpened] follows once it has.
 */
@Composable
fun PinEntry(
    title: String,
    length: Int,
    check: suspend (String) -> PinAttempt,
    modifier: Modifier = Modifier,
    message: String? = null,
    onBiometric: (() -> Unit)? = null,
    lock: Boolean = false,
    onOpened: () -> Unit = {},
    onForgot: (() -> Unit)? = null,
    header: @Composable () -> Unit = {},
) {
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val motion = Blesk.motion
    val dim = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    var entered by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var waitUntil by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val wrong = stringResource(R.string.lock_wrong)
    val password = rememberTextFieldState()
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
        if (busy) return
        busy = true
        scope.launch {
            when (val result = check(code)) {
                PinAttempt.Ok -> {
                    if (lock) {
                        haptics.detach()
                        open.animateTo(1f, motion.signature())
                    } else {
                        // A step of setting a code: the next step starts with empty slots.
                        entered = ""
                        password.clearText()
                    }
                    onOpened()
                }
                is PinAttempt.Wrong -> {
                    haptics.refuse()
                    error = wrong
                    waitUntil = System.currentTimeMillis() + result.waitMillis
                    dim.snapTo(1f)
                    dim.animateTo(0f, tween(DIM_MILLIS))
                    entered = ""
                    password.clearText()
                }
            }
            busy = false
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
        if (lock) {
            Icon(
                icon = BleskIcons.Lock,
                contentDescription = null,
                size = 32.dp,
                modifier = Modifier
                    .padding(bottom = 20.dp)
                    .graphicsLayer {
                        translationY = -LIFT * open.value * density
                        alpha = 1f - open.value
                    },
            )
        }
        header()
        Text(title, style = Blesk.type.title, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(28.dp))
        if (length > 0) {
            PinDots(length = length, entered = entered.length, dim = { dim.value })
        } else {
            SecretInput(
                state = password,
                label = stringResource(R.string.lock_password),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                onDone = { if (!waiting) submit(password.text.toString()) },
                modifier = Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - DIM_DEPTH * dim.value },
            )
        }
        Text(
            text = waitText ?: error ?: message ?: " ",
            style = Blesk.type.label,
            color = if (error != null || waiting) Blesk.colors.danger else Blesk.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp, bottom = 28.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (length > 0) {
            Keypad(
                onDigit = { digit ->
                    if (waiting || busy || entered.length >= length) return@Keypad
                    error = null
                    entered += digit
                    if (entered.length == length) submit(entered)
                },
                onDelete = { entered = entered.dropLast(1) },
                onBiometric = onBiometric,
            )
        } else {
            Button(
                text = stringResource(R.string.lock_open),
                onClick = { if (!waiting) submit(password.text.toString()) },
                modifier = Modifier.fillMaxWidth(),
            )
            onBiometric?.let { biometric ->
                Button(
                    text = stringResource(R.string.lock_biometric),
                    icon = BleskIcons.Fingerprint,
                    kind = ButtonKind.Secondary,
                    onClick = biometric,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                )
            }
        }
        onForgot?.let { forgot ->
            Button(
                text = stringResource(R.string.lock_forgot),
                kind = ButtonKind.Secondary,
                onClick = forgot,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}

private const val TICK_MILLIS = 500L
private const val DIM_MILLIS = 600
private const val DIM_DEPTH = 0.65f
private const val LIFT = 48f
