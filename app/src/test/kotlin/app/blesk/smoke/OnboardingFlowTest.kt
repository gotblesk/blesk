package app.blesk.smoke

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.AppContainer
import app.blesk.BleskApplication
import app.blesk.MainActivity
import app.blesk.Shared
import app.blesk.core.data.settings.StartState
import app.blesk.core.network.ServerAddress
import app.blesk.screenshots.FrozenApplication
import app.blesk.shared.protocol.Invites
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The first start of the whole app: the welcome screen and the ways out of it. Nothing here reaches a server. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = FrozenApplication::class)
class OnboardingFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val container: AppContainer
        get() = (compose.activity.application as BleskApplication).container

    private fun waitFor(text: String) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun theFirstStartIsTheWelcomeScreen() {
        waitFor(CREATE)

        compose.onNodeWithText(CREATE).assertIsDisplayed()
        compose.onNodeWithContentDescription(HAVE_ACCOUNT_SOON).assertIsDisplayed()
        compose.onNodeWithText(DEMO).assertIsDisplayed()
        compose.onNodeWithText(OTHER_SERVER).assertIsDisplayed()
        compose.onNodeWithContentDescription("Новый чат").assertDoesNotExist()
        // The demo world is not built until it is chosen.
        assertNull(container.chats.value)
    }

    @Test
    fun anAccountLaterSaysSo() {
        waitFor(CREATE)

        compose.onNodeWithContentDescription(HAVE_ACCOUNT_SOON).performClick()

        waitFor("Вход по фразе восстановления появится в одном из следующих обновлений")
    }

    @Test
    fun theDemoOpensTheChatsAndIsRemembered() {
        compose.enterDemo()

        compose.onNodeWithContentDescription("Новый чат").assertIsDisplayed()
        // The chats took the welcome screen's place rather than covering it: back leaves the app.
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
        runBlocking { withTimeout(WAIT_MILLIS) { container.onboarding.start.first { it == StartState.Demo } } }
    }

    @Test
    fun backOnTheWelcomeScreenLeavesTheApp() {
        waitFor(CREATE)

        // Nothing in the app takes it, so the system does, as on any first screen.
        assertFalse(compose.activity.onBackPressedDispatcher.hasEnabledCallbacks())
    }

    @Test
    fun aBadAddressIsRefusedOnTheServerStep() {
        waitFor(OTHER_SERVER)
        compose.onNodeWithText(OTHER_SERVER).performClick()
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)

        compose.onNodeWithContentDescription("Адрес сервера").performTextInput("не адрес")
        compose.onNodeWithText("Готово").performClick()

        waitFor("Не похоже на адрес сервера")
    }

    @Test
    fun anInviteLinkOnTheWelcomeScreenOpensTheInviteStep() {
        waitFor(CREATE)

        container.linkRequest.value = Invites.link(CODE)

        waitFor("Инвайт-код")
        waitFor(CODE)
    }

    @Test
    fun anInviteLinksCodeIsStillThereAfterGoingBack() {
        waitFor(CREATE)
        container.linkRequest.value = Invites.link(CODE)
        waitFor(CODE)

        goBack()
        waitFor(CREATE)
        // blesk's own server's code opens the invite step again, as «Создать аккаунт» would, without asking the server.
        container.linkRequest.value = OWN_SERVER_CODE

        waitFor(INVITE_TITLE)
        waitFor(CODE)
    }

    @Test
    fun aCodeTypedOverTheLinksIsKeptOnComingBack() {
        waitFor(CREATE)
        container.linkRequest.value = Invites.link(CODE)
        waitFor(CODE)

        compose.onNodeWithContentDescription(INVITE_FIELD).performTextReplacement(OTHER_CODE)
        compose.onNodeWithText("Дальше").performClick()
        waitFor("Как тебя найдут")
        goBack()

        waitFor(OTHER_CODE)
        compose.onNodeWithText(CODE).assertDoesNotExist()
    }

    @Test
    fun aLinkThatNeedsAnAccountKeepsTheChosenServer() {
        waitFor(CREATE)
        val chosen = ServerAddress("https://chat.example.ru")
        container.signUp.server = chosen
        waitFor("Сервер: chat.example.ru")

        container.linkRequest.value = "https://blesk.su/@vera.s"

        waitFor(INVITE_TITLE)
        assertEquals(chosen, container.signUp.server)
    }

    @Test
    fun aShareWaitsForTheChats() {
        waitFor(CREATE)

        container.shareRequest.value = Shared("https://blesk.su", emptyList())
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.onNodeWithText(CREATE).assertIsDisplayed()

        compose.enterDemo()
        waitFor("Переслать в…")
    }

    @Test
    fun aChatFromANotificationWaitsForTheChats() {
        waitFor(CREATE)
        val echo = runBlocking { container.demo.conversations.first() }.first { it.title == "Эхо" }.id

        container.openRequest.value = echo
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.onNodeWithText(CREATE).assertIsDisplayed()

        compose.enterDemo()
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithContentDescription("Сообщение").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun theWelcomeActionsSitInTheMiddleOfASmallScreen() {
        waitFor(CREATE)
        listOf(
            compose.onNodeWithText(CREATE),
            compose.onNodeWithContentDescription(HAVE_ACCOUNT_SOON),
            compose.onNodeWithText(DEMO),
            compose.onNodeWithText(OTHER_SERVER),
        ).forEach(::assertInSmokeBand)

        // As the smoke test comes back from the server step: that server is named under the ways in.
        container.signUp.server = ServerAddress("https://127.0.0.1:8443", "sha256/AAAA")
        waitFor("Сервер: 127.0.0.1:8443")
        assertInSmokeBand(compose.onNodeWithText(CREATE))
    }

    private fun goBack() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        compose.waitForIdle()
    }

    /** The middle of the screen `scripts/ci/ui.py tap` reaches, clear of the floating lenses. */
    private fun assertInSmokeBand(node: SemanticsNodeInteraction) {
        val bounds = node.getUnclippedBoundsInRoot()
        val share = (bounds.top + bounds.bottom) / 2f / compose.onRoot().getUnclippedBoundsInRoot().bottom
        assertTrue("centre at $share of the screen", share in SMOKE_BAND)
    }

    private companion object {
        const val CREATE = "Создать аккаунт"
        const val HAVE_ACCOUNT_SOON = "У меня уже есть аккаунт, скоро"
        const val DEMO = "Попробовать демо"
        const val OTHER_SERVER = "Другой сервер"
        const val CODE = "abcdefghjk23"
        const val OTHER_CODE = "mnpqrstuvw45"
        const val OWN_SERVER_CODE = "blesk://server?url=https://api.blesk.su"
        const val INVITE_TITLE = "Инвайт-код"
        const val INVITE_FIELD = "Код или ссылка"
        const val WAIT_MILLIS = 5_000L
        const val SETTLE_MILLIS = 1_000L
        val SMOKE_BAND = 0.15f..0.78f
    }
}
