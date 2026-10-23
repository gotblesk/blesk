package app.blesk.core.design.component

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.contrastRatio
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The default locale stays English on purpose: the wait must read in Russian numbers whatever the phone's language. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ControlsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun aBusyButtonTakesNoTapAndSaysWait() {
        var taps = 0
        var busy by mutableStateOf(true)
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = false)) { Button("Создать аккаунт", onClick = { taps++ }, busy = busy) }
        }
        compose.onNodeWithText("Создать аккаунт")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Подожди"))
            .performClick()
        assertEquals(0, taps)

        busy = false
        compose.onNodeWithText("Создать аккаунт")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
            .performClick()
        assertEquals(1, taps)
    }

    @Test
    fun aBusyButtonDimsWhereTheLightStandsStill() {
        var busy by mutableStateOf(false)
        showButton(BleskMotion(reduced = true)) { busy }
        val idle = edge()
        busy = true
        assertNotEquals(idle, edge())
    }

    @Test
    fun lightKeepsPassingOverABusyButton() {
        compose.mainClock.autoAdvance = false
        showButton(BleskMotion(reduced = false)) { true }
        compose.mainClock.advanceTimeBy(400)
        val early = edge()
        compose.mainClock.advanceTimeBy(800)
        assertNotEquals(early, edge())
    }

    @Test
    fun aBusyLabelStaysReadableWhereTheLightStandsStill() {
        val label = showBusyInEveryTheme(BleskMotion(reduced = true))
        ThemeSpec.presets.forEach { preset ->
            theme = preset
            compose.waitForIdle()
            val ratio = contrastRatio(label(), edge())
            assertTrue("${preset.preset}: $ratio", ratio >= ContrastLevel.BODY_TEXT)
        }
    }

    @Test
    fun aBusyLabelStaysReadableAsTheLightCrossesIt() {
        compose.mainClock.autoAdvance = false
        val label = showBusyInEveryTheme(BleskMotion(reduced = false))
        ThemeSpec.presets.forEach { preset ->
            theme = preset
            compose.mainClock.advanceTimeByFrame()
            // A whole pass, looked at near the edge of the button, where the light enters it.
            val seen = (1..PASS_STEPS).map {
                compose.mainClock.advanceTimeBy(PASS_STEP_MILLIS)
                edge()
            }
            val worst = seen.minOf { contrastRatio(label(), it) }
            assertTrue("${preset.preset}: $worst", worst >= ContrastLevel.BODY_TEXT)
            assertTrue("${preset.preset}: no light passed", seen.distinct().size > 1)
        }
    }

    private var theme by mutableStateOf(ThemeSpec.Light)

    /** A busy button in [theme]; gives the colour its label is written in. */
    private fun showBusyInEveryTheme(motion: BleskMotion): () -> Color {
        var label = Color.Unspecified
        compose.setContent {
            BleskTheme(Appearance(theme = theme), motion) {
                label = Blesk.colors.onAccent
                Button("Создать аккаунт", onClick = {}, Modifier.width(200.dp), busy = true)
            }
        }
        return { label }
    }

    private fun showButton(motion: BleskMotion, busy: () -> Boolean) {
        compose.setContent {
            BleskTheme(motion = motion) { Button("Создать аккаунт", onClick = {}, Modifier.width(200.dp), busy = busy()) }
        }
    }

    /** The colour near the start of the button. */
    private fun edge(): Color {
        val picture = compose.onNodeWithText("Создать аккаунт").captureToImage().toPixelMap()
        return picture[picture.width / 20, picture.height / 2]
    }

    @Test
    fun aCheckRowIsACheckbox() {
        var subscribed by mutableStateOf(true)
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) {
                CheckRow("Подписаться на канал blesk", subscribed, onCheckedChange = { subscribed = it }, subtitle = "Новости и подсказки")
            }
        }
        val row = compose.onNodeWithText("Подписаться на канал blesk")
        row.assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)).assertIsOn().performClick()
        assertFalse(subscribed)
        row.assertIsOff()
    }

    @Test
    fun waitTextFollowsRussianNumbers() {
        val expected = linkedMapOf(
            0L to "1 секунду",
            1_000L to "1 секунду",
            1_001L to "2 секунды",
            2_000L to "2 секунды",
            5_000L to "5 секунд",
            11_000L to "11 секунд",
            21_000L to "21 секунду",
            22_000L to "22 секунды",
            30_000L to "30 секунд",
            59_001L to "1 минуту",
            60_000L to "1 минуту",
            61_000L to "2 минуты",
            300_000L to "5 минут",
            900_000L to "15 минут",
        )
        val shown = mutableMapOf<Long, String>()
        compose.setContent {
            BleskTheme(motion = BleskMotion(reduced = true)) { expected.keys.forEach { millis -> shown[millis] = formatWait(millis) } }
        }
        compose.waitForIdle()
        assertEquals(expected, shown)
    }

    private companion object {
        const val PASS_STEPS = 32
        const val PASS_STEP_MILLIS = 50L
    }
}
