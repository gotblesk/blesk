package app.blesk.core.design.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Scenario 87 as it is typed: the field keeps a name as a name can be, and asks the server sparingly. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class UsernameFieldTest {

    @get:Rule
    val compose = createComposeRule()

    private val asked = mutableListOf<String>()
    private var done = 0
    private val answers = mapOf(
        "anya" to UsernameCheck(false, UsernameVerdict.TAKEN, "anya", listOf("anya27", "anya.m")),
    )

    private val field get() = compose.onNodeWithContentDescription(LABEL)
    private val readOutPolitely = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun show() {
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                val input = rememberUsernameInput()
                val checks = rememberUsernameChecks(input) { name ->
                    asked += name
                    answers[name] ?: UsernameCheck(true, UsernameVerdict.FREE, name)
                }
                TextInput(
                    label = LABEL,
                    state = input.field,
                    prefix = "@",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    inputTransformation = input.transformation,
                    onKeyboardAction = { done++ },
                    supporting = {
                        UsernameNote(checks.status, input.notice, unknownText = "Не получилось проверить имя")
                        UsernameSuggestions(checks.status, onPick = input::take)
                    },
                )
            }
        }
    }

    @Test
    fun capitalLettersBecomeSmall() {
        show()
        field.performTextInput("Egor")
        field.assertTextEquals("egor")
    }

    @Test
    fun aCapitalTypedInTheMiddleKeepsTheCursorAfterIt() {
        show()
        field.performTextInput("gor")
        field.performTextInputSelection(TextRange(0))
        field.performTextInput("E")
        field.assertTextEquals("egor")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(1)))
    }

    @Test
    fun aPastedDirectionMarkDisappears() {
        show()
        field.performTextInput("‮egor​")
        field.assertTextEquals("egor")
    }

    @Test
    fun cyrillicIsDroppedAndExplained() {
        show()
        field.performTextInput("егор")
        field.assertTextEquals("")
        compose.onNodeWithText("Только латиница — переключи раскладку").assertExists()
        field.performTextInput("e")
        compose.onNodeWithText("Только латиница — переключи раскладку").assertDoesNotExist()
    }

    @Test
    fun checksWaitForAPauseAndRemember() {
        show()
        compose.mainClock.autoAdvance = false
        field.performTextReplacement("anya")
        compose.mainClock.advanceTimeBy(399)
        assertEquals(emptyList<String>(), asked)
        compose.mainClock.advanceTimeBy(100)
        assertEquals(listOf("anya"), asked)
        compose.onNodeWithText("@anya уже занято").assert(readOutPolitely)

        field.performTextReplacement("anya1")
        compose.mainClock.advanceTimeBy(100)
        field.performTextReplacement("anya")
        compose.mainClock.advanceTimeBy(1_000)
        assertEquals(listOf("anya"), asked)
        compose.onNodeWithText("@anya уже занято").assertExists()
    }

    @Test
    fun aSuggestionTapSendsNoRequest() {
        show()
        compose.mainClock.autoAdvance = false
        field.performTextReplacement("anya")
        compose.mainClock.advanceTimeBy(500)
        compose.onNodeWithContentDescription("Взять имя anya27").performClick()
        compose.mainClock.advanceTimeBy(100)
        field.assertTextEquals("anya27")
        compose.onNodeWithText("@anya27 свободно").assertExists()
        assertEquals(listOf("anya"), asked)
    }

    @Test
    fun theKeyboardsActionKeyRunsTheStepsAction() {
        show()
        field.performTextInput("egor")
        field.performImeAction()
        assertEquals(1, done)
    }

    private companion object {
        const val LABEL = "Имя пользователя"
    }
}
