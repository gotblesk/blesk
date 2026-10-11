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

/** New code: pick 4 or 6 digits, type it, type it again. */
@Composable
fun PinSetup(onDone: (String) -> Unit, modifier: Modifier = Modifier) {
    var length by rememberSaveable { mutableIntStateOf(4) }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var mismatch by rememberSaveable { mutableStateOf(false) }
    val options = persistentListOf(stringResource(R.string.pin_digits_4), stringResource(R.string.pin_digits_6))
    val pending = first
    PinEntry(
        title = stringResource(if (pending == null) R.string.pin_new_title else R.string.pin_repeat_title),
        length = length,
        message = if (mismatch) stringResource(R.string.pin_mismatch) else null,
        modifier = modifier,
        check = { code ->
            when {
                pending == null -> {
                    first = code
                    mismatch = false
                    PinAttempt.Ok
                }
                pending == code -> {
                    onDone(code)
                    PinAttempt.Ok
                }
                else -> {
                    first = null
                    mismatch = true
                    PinAttempt.Wrong(0)
                }
            }
        },
        header = {
            if (pending == null) {
                Segmented(
                    options = options,
                    selected = if (length == 4) 0 else 1,
                    onSelect = { length = if (it == 0) 4 else 6 },
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        },
    )
}
