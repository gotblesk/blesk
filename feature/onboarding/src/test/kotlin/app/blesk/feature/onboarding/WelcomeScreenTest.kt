package app.blesk.feature.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.Notices
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/** The first screen on the CI emulator's 320×640 dp: every way in reachable, honest about what is not ready. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class WelcomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var notices: Notices
    private var state by mutableStateOf(WelcomeState())
    private val chosen = mutableListOf<String>()
    private val readOutPolitely = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    private fun show(
        reduced: Boolean = true,
        haveAccount: Boolean = false,
        update: Boolean = false,
        fontScale: Float = 1f,
        appearance: Appearance = Appearance(),
        still: Boolean = reduced,
    ) {
        val actions = WelcomeActions(
            onCreate = { chosen += "create" },
            onHaveAccount = { chosen += "account" }.takeIf { haveAccount },
            onDemo = { chosen += "demo" },
            onOtherServer = { chosen += "server" },
            onUpdate = { chosen += "update" }.takeIf { update },
        )
        compose.setContent {
            val scope = rememberCoroutineScope()
            notices = remember { Notices(scope) }
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BleskTheme(appearance, BleskMotion(reduced = reduced, still = still)) {
                    CompositionLocalProvider(LocalNotices provides notices) { WelcomeScreen(state, actions) }
                }
            }
        }
    }

    @Test
    fun theWaysInSitWhereTheSmokeTestTaps() {
        show()
        listOf(
            compose.onNodeWithText(CREATE),
            compose.onNodeWithContentDescription("$HAVE_ACCOUNT, скоро"),
            compose.onNodeWithText(DEMO),
            compose.onNodeWithText(OTHER_SERVER),
        ).forEach { node ->
            val share = compose.heightShare(node)
            assertTrue("centre at $share of the height", share in SmokeBand)
        }
    }

    @Test
    fun theWaysInAreLabelledAndBigEnough() {
        state = WelcomeState(server = "chat.example.ru", problem = AccessProblem.AppTooOld)
        show(update = true)
        val problems = compose.unreachableControls("welcome")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun aLoginNotReadyYetSaysWhenItComes() {
        show()
        compose.onNodeWithContentDescription("$HAVE_ACCOUNT, скоро").performClick()
        assertEquals("Вход по фразе восстановления появится в одном из следующих обновлений", notices.current?.text)
        assertEquals(emptyList<String>(), chosen)
    }

    @Test
    fun aReadyLoginIsAnOrdinaryWayIn() {
        show(haveAccount = true)
        compose.onNodeWithContentDescription("$HAVE_ACCOUNT, скоро").assertDoesNotExist()
        compose.onNodeWithText(HAVE_ACCOUNT).performClick()
        assertEquals(listOf("account"), chosen)
        assertNull(notices.current)
    }

    @Test
    fun aChoiceComesAwayFromTheDropBeforeItIsCarriedOut() {
        compose.mainClock.autoAdvance = false
        show(reduced = false)
        compose.onNodeWithText(DEMO).performClick()
        compose.onNodeWithText(DEMO).performClick()
        assertEquals(emptyList<String>(), chosen)
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        assertEquals(listOf("demo"), chosen)
    }

    @Test
    fun withoutAnimationsAChoiceIsCarriedOutAtOnce() {
        compose.mainClock.autoAdvance = false
        show(reduced = true)
        compose.onNodeWithText(DEMO).performClick()
        assertEquals(listOf("demo"), chosen)
    }

    @Test
    fun creatingStartsAtOnceAndTakesNoSecondTap() {
        compose.mainClock.autoAdvance = false
        show(reduced = false)
        compose.onNodeWithText(CREATE).performClick()
        assertEquals(listOf("create"), chosen)
        state = WelcomeState(busy = true)
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        compose.onNodeWithText(CREATE).performClick()
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        assertEquals(listOf("create"), chosen)
    }

    @Test
    fun aServerSlowToAnswerCanBeLeftForTheDemo() {
        compose.mainClock.autoAdvance = false
        show(reduced = false)
        compose.onNodeWithText(CREATE).performClick()
        state = WelcomeState(busy = true)
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        compose.onNodeWithText(DEMO).performClick()
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        assertEquals(listOf("create", "demo"), chosen)
    }

    @Test
    fun withoutAnimationsTheDemoIsThereWhileCreating() {
        state = WelcomeState(busy = true)
        show(reduced = true)
        compose.onNodeWithText(DEMO).performClick()
        compose.onNodeWithText(OTHER_SERVER).performClick()
        assertEquals(listOf("demo", "server"), chosen)
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun theWaysInKeepAllTheirWordsAtTheLargestFont() {
        show(fontScale = 2f)
        listOf(CREATE, HAVE_ACCOUNT, DEMO).forEach { text ->
            val layout = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
                .config[SemanticsActions.GetTextLayoutResult].action?.invoke(layout)
            assertFalse("«$text» is cut short", layout.single().cutShort())
        }
    }

    @Test
    @Config(qualifiers = "w400dp-h860dp-xxhdpi")
    fun theLitWayIsStuckToTheDropLikeTheOthers() {
        show(reduced = false, still = true, appearance = Appearance(theme = ThemeSpec.Light))
        val create = compose.onNodeWithText(CREATE).getBoundsInRoot()
        val haveAccount = compose.onNodeWithContentDescription("$HAVE_ACCOUNT, скоро").getBoundsInRoot()
        val demo = compose.onNodeWithText(DEMO).getBoundsInRoot()
        val picture = compose.onRoot().captureToImage().toPixelMap()
        fun bridge(above: DpRect, below: DpRect): Color = with(compose.density) {
            picture[((above.left + above.right) / 2).roundToPx(), ((above.bottom + below.top) / 2).roundToPx()]
        }
        val lit = bridge(create, haveAccount)
        val plain = bridge(haveAccount, demo)
        val apart = listOf(lit.red - plain.red, lit.green - plain.green, lit.blue - plain.blue).maxOf { abs(it) }
        assertTrue("$lit against $plain", apart <= BRIDGE_TOLERANCE)
    }

    @Test
    fun aFailedAttemptBringsTheDropBackTogether() {
        compose.mainClock.autoAdvance = false
        show(reduced = false)
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        val together = demoBelowCreate()
        compose.onNodeWithText(CREATE).performClick()
        state = WelcomeState(busy = true)
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        assertEquals(together + 24.dp, demoBelowCreate())
        state = WelcomeState(problem = AccessProblem.Unreachable(otherServer = false))
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        assertEquals(together, demoBelowCreate())
    }

    private fun demoBelowCreate() =
        compose.onNodeWithText(DEMO).getUnclippedBoundsInRoot().top - compose.onNodeWithText(CREATE).getUnclippedBoundsInRoot().top

    @Test
    fun theConnectingLineWaitsAMoment() {
        compose.mainClock.autoAdvance = false
        state = WelcomeState(busy = true)
        show()
        compose.mainClock.advanceTimeBy(299)
        compose.onNodeWithText(CONNECTING).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(FRAME_MILLIS)
        compose.onNodeWithText(CONNECTING).assert(readOutPolitely)
    }

    @Test
    fun aProblemIsReadOutPolitely() {
        state = WelcomeState(problem = AccessProblem.Unreachable(otherServer = false))
        show()
        compose.onNodeWithText("Сервер не отвечает. Проверь интернет и попробуй ещё раз.").assert(readOutPolitely)
    }

    @Test
    fun aWaitIsSaidInWords() {
        state = WelcomeState(problem = AccessProblem.TooManyAttempts(waitMillis = 120_000))
        show()
        compose.onNodeWithText("Слишком много попыток. Подожди 2 минуты.").assertExists()
    }

    @Test
    fun anOutdatedAppOffersItsUpdate() {
        state = WelcomeState(problem = AccessProblem.AppTooOld)
        show(update = true)
        compose.onNodeWithText("Обновить").performClick()
        assertEquals(listOf("update"), chosen)
    }

    @Test
    fun anotherServerIsNamedAndCanBeChanged() {
        state = WelcomeState(server = "chat.example.ru")
        show()
        compose.onNodeWithText("Сервер: chat.example.ru").performClick()
        assertEquals(listOf("server"), chosen)
    }

    private companion object {
        const val CREATE = "Создать аккаунт"
        const val HAVE_ACCOUNT = "У меня уже есть аккаунт"
        const val DEMO = "Попробовать демо"
        const val OTHER_SERVER = "Другой сервер"
        const val CONNECTING = "Подключаемся к серверу…"
        const val DETACH_MILLIS = 600L
        const val SETTLE_MILLIS = 1_500L
        const val FRAME_MILLIS = 16L

        /** About one step of 255 in each channel: the bridges are the same drop, drawn the same. */
        const val BRIDGE_TOLERANCE = 0.01f
    }
}
