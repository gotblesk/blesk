package app.blesk.feature.onboarding

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The steps after the account is made, on the CI emulator's 320×640 dp: name, code, theme and the glint. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class SetupStepsTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()
    private val name = TextFieldState()
    private var profile by mutableStateOf(ProfileStepState(username = "egor"))
    private var subscribe by mutableStateOf(true)

    private fun show(content: @Composable () -> Unit) {
        compose.setContent { BleskTheme(motion = BleskMotion(reduced = true)) { content() } }
    }

    private var framing by mutableStateOf(false)

    private fun profileStep() = show {
        ProfileStepScreen(
            name = name,
            state = profile,
            actions = ProfileStepActions(
                onPickPhoto = { done += "pick" },
                onRemovePhoto = { done += "remove" },
                onNext = { done += "next" },
                onSkip = { done += "skip" },
            ),
            // A frame with nothing to tap in its lower half, as the photo's frame has.
            crop = if (framing) ({ Box(Modifier.fillMaxSize().background(Color.Black)) }) else null,
        )
    }

    private fun glint(channel: Boolean) = show {
        GlintStepScreen(
            channel = channel,
            subscribe = subscribe,
            onSubscribeChange = {
                subscribe = it
                done += "subscribe:$it"
            },
            onDone = { done += "chats" },
        )
    }

    private fun assertAtTheBottom(vararg texts: String) {
        texts.forEach { text ->
            val share = compose.heightShare(compose.onNodeWithText(text))
            assertTrue("$text at $share of the height", share > SmokeBand.endInclusive)
        }
    }

    private fun assertReachable(screen: String) {
        val problems = compose.unreachableControls(screen)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theNameIsTypedInTheMiddleAndTheStepLeftAtTheBottom() {
        profile = ProfileStepState(username = "egor", photo = photo())
        profileStep()
        val share = compose.heightShare(compose.onNodeWithContentDescription("Имя"))
        assertTrue("the name field at $share of the height", share in SmokeBand)
        assertAtTheBottom("Дальше", "Пропустить")
        assertReachable("profile")
    }

    @Test
    fun theAvatarPicksAPhotoAndOnlyAPhotoCanBeRemoved() {
        profileStep()
        compose.onNodeWithText("Убрать фото").assertDoesNotExist()
        compose.onNodeWithContentDescription("Выбрать фото").assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        compose.onNodeWithContentDescription("Выбрать фото").performClick()
        profile = ProfileStepState(username = "egor", photo = photo(), photoVersion = 1)
        compose.onNodeWithText("Убрать фото").performClick()
        assertEquals(listOf("pick", "remove"), done)
    }

    @Test
    fun whileAPhotoIsFramedTheStepUnderItIsOutOfReach() {
        profileStep()
        val next = compose.onNodeWithText("Дальше").getUnclippedBoundsInRoot()
        framing = true

        compose.onNodeWithText("Дальше").assertDoesNotExist()
        compose.onNodeWithText("Пропустить").assertDoesNotExist()
        compose.onNodeWithContentDescription("Имя").assertDoesNotExist()
        compose.onRoot().performTouchInput { click(Offset(((next.left + next.right) / 2).toPx(), ((next.top + next.bottom) / 2).toPx())) }
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun theNameStepGoesOnFromTheKeyboardOrIsSkipped() {
        profileStep()
        compose.onNodeWithContentDescription("Имя").performImeAction()
        compose.onNodeWithText("Пропустить").performClick()
        assertEquals(listOf("next", "skip"), done)
    }

    @Test
    fun aNameStopsAtItsLongest() {
        profileStep()
        compose.onNodeWithContentDescription("Имя").performTextInput("а".repeat(80))
        assertEquals(64, name.text.length)
    }

    @Test
    fun theAvatarShowsTheUsernameUntilTheNameHasSomethingToSee() {
        assertEquals("egor", avatarName("", "egor"))
        assertEquals("egor", avatarName("\u200B\u202E\u3164 ", "egor"))
        assertEquals("Ева", avatarName("  Ева\u202E ", "egor"))
    }

    @Test
    fun aNameWithNothingVisibleIsExplained() {
        profileStep()
        compose.onNodeWithText(INVISIBLE).assertDoesNotExist()
        profile = ProfileStepState(username = "egor", refused = true)
        compose.onNodeWithText(INVISIBLE).assertExists()
    }

    @Test
    fun theCodeIsOfferedAtTheBottom() {
        show { CodeOfferScreen(onEnable = { done += "on" }, onLater = { done += "later" }) }
        assertAtTheBottom("Включить", "Не сейчас")
        assertReachable("code")
        compose.onNodeWithText("Включить").performClick()
        compose.onNodeWithText("Не сейчас").performClick()
        assertEquals(listOf("on", "later"), done)
    }

    @Test
    fun theThemeIsChosenOnItsPreview() {
        show {
            ThemeStepScreen(
                picker = { Button("Тёмная", onClick = { done += "dark" }) },
                preview = { Text("Пример чата") },
                onNext = { done += "next" },
            )
        }
        assertAtTheBottom("Дальше")
        assertReachable("theme")
        compose.onNodeWithText("Пример чата").assertExists()
        compose.onNodeWithText("Тёмная").performClick()
        compose.onNodeWithText("Дальше").performClick()
        assertEquals(listOf("dark", "next"), done)
    }

    @Test
    fun theGlintIsAlsoSaidInWords() {
        glint(channel = false)
        assertAtTheBottom("В чаты")
        assertReachable("glint")
        // Each example is one stop for TalkBack, its kind of protection read with its name.
        compose.onNode(hasText("Личный чат") and hasText("Сквозное шифрование")).assertExists()
        compose.onNode(hasText("Канал") and hasText("Без сквозного шифрования")).assertExists()
        // One claim per example, in the brief's words: a second, shorter one would say less than the truth.
        compose.onNodeWithText("без шифрования").assertDoesNotExist()
    }

    @Test
    fun theChannelIsOfferedOnlyWhenTheServerHasOne() {
        glint(channel = false)
        compose.onNodeWithText(CHANNEL).assertDoesNotExist()
    }

    @Test
    fun theChannelIsTickedAndCanBeUnticked() {
        glint(channel = true)
        compose.onNodeWithText(CHANNEL).assertIsOn()
        compose.onNodeWithText(CHANNEL).performClick()
        compose.onNodeWithText(CHANNEL).assertIsOff()
        compose.onNodeWithText("В чаты").performClick()
        assertEquals(listOf("subscribe:false", "chats"), done)
    }

    private fun photo() = Bitmap.createBitmap(PHOTO_SIDE, PHOTO_SIDE, Bitmap.Config.ARGB_8888).asImageBitmap()

    private companion object {
        const val INVISIBLE = "В имени нужна хотя бы одна видимая буква, цифра или эмодзи"
        const val CHANNEL = "Подписаться на канал blesk"
        const val PHOTO_SIDE = 64
    }
}
