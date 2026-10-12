package app.blesk.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

/** A labelled matte text field. The label doubles as the TalkBack name. */
@Composable
fun TextInput(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    hint: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    inputTransformation: InputTransformation? = null,
) {
    Column(modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 6.dp)) {
        Text(label, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blesk.colors.surfaceStrong)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicTextField(
                state = state,
                textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                cursorBrush = SolidColor(Blesk.colors.accent),
                lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                keyboardOptions = keyboardOptions,
                inputTransformation = inputTransformation,
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
        if (hint != null) {
            Text(hint, style = Blesk.type.caption, color = Blesk.colors.textTertiary, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
        }
    }
}

/** A labelled matte field for a password: typed characters show as dots, the last one briefly. */
@Composable
fun SecretInput(
    label: String,
    state: TextFieldState,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onDone: () -> Unit = {},
) {
    Column(modifier.padding(vertical = 6.dp)) {
        Text(label, style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Blesk.colors.surfaceStrong)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            BasicSecureTextField(
                state = state,
                textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                cursorBrush = SolidColor(Blesk.colors.accent),
                keyboardOptions = keyboardOptions,
                onKeyboardAction = { onDone() },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = label },
            )
        }
    }
}
