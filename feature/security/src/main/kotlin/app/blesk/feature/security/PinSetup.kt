package app.blesk.feature.security

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Segmented
import kotlinx.collections.immutable.persistentListOf

/** New code: pick 4 or 6 digits or a password, type it, type it again. */
@Composable
fun PinSetup(onDone: (String) -> Unit, modifier: Modifier = Modifier, minPassword: Int = 6) {
    // 4, 6, or 0 for a password typed on the keyboard.
    var length by rememberSaveable { mutableIntStateOf(4) }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var problem by rememberSaveable { mutableStateOf<Int?>(null) }
    val options = persistentListOf(
        stringResource(R.string.pin_digits_4),
        stringResource(R.string.pin_digits_6),
        stringResource(R.string.pin_password),
    )
    val pending = first
    PinEntry(
        title = stringResource(if (pending == null) R.string.pin_new_title else R.string.pin_repeat_title),
        length = length,
        message = when (problem) {
            null -> if (length == 0 && pending == null) stringResource(R.string.pin_password_hint, minPassword) else null
            else -> stringResource(problem ?: R.string.pin_mismatch)
        },
        modifier = modifier,
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
                    onDone(code)
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
}
