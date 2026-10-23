package app.blesk.core.design.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import app.blesk.shared.protocol.Usernames

/** Why something typed into a @username field vanished, said under it. */
enum class TypedNotice {
    /** Letters of another alphabet were taken out: the keyboard is likely on the wrong layout. */
    LatinOnly,

    /** The name was longer than [Usernames.MAX] and its end was cut. */
    TooLong,
}

/**
 * The text of a @username field, kept as a name can be while it is typed or pasted: capitals
 * become small, marks, direction and invisible characters go, a profile link gives its name
 * (see [Usernames.typed]). Give [transformation] to the field; [notice] says why something
 * vanished.
 */
@Stable
class UsernameInput internal constructor(val field: TextFieldState) {
    var notice: TypedNotice? by mutableStateOf(null)
        private set

    // [field] in a getter would name the backing field, hence `this`.
    val text: String get() = this.field.text.toString()

    val transformation = InputTransformation {
        val typed = Usernames.typed(asCharSequence())
        notice = when {
            typed.droppedNonLatin -> TypedNotice.LatinOnly
            typed.cut -> TypedNotice.TooLong
            else -> null
        }
        // Replacing the text ends the keyboard's composition, so text that is already clean is left alone.
        if (typed.text.contentEquals(asCharSequence())) return@InputTransformation
        val cursor = Usernames.typed(asCharSequence().subSequence(0, selection.min)).text.length
        replace(0, length, typed.text)
        selection = TextRange(cursor.coerceAtMost(length))
    }

    /** Puts [name] in the field, as when a free name offered under it is tapped. */
    fun take(name: String) {
        field.setTextAndPlaceCursorAtEnd(name)
        notice = null
    }
}

@Composable
fun rememberUsernameInput(initial: String = ""): UsernameInput {
    val field = rememberTextFieldState(initial)
    return remember(field) { UsernameInput(field) }
}
