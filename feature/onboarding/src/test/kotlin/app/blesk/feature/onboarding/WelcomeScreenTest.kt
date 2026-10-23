package app.blesk.feature.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.Notices
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

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

    private fun show(reduced: Boolean = true, haveAccount: Boolean = false, update: Boolean = false) {
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
            BleskTheme(motion = BleskMotion(reduced = reduced)) {
                CompositionLocalProvider(LocalNotices provides notices) { WelcomeScreen(state, actions) }
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
        compose.onNodeWithText(DEMO).performClick()
        compose.mainClock.advanceTimeBy(DETACH_MILLIS)
        assertEquals(listOf("create"), chosen)
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
    }
}
