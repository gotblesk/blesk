package app.blesk.onboarding

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.design.theme.LocalClock
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerTrouble
import app.blesk.core.motion.BleskMotion
import app.blesk.core.network.ServerAddress
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant

/**
 * The name step after a sign-up that did not succeed: the server may have made the account, and then calls the
 * name taken, yet the name is this phone's to finish.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class UsernameRouteTest {
    @get:Rule
    val compose = createComposeRule()

    private var now = Instant.parse("2026-10-23T12:00:00Z")

    /** Names the server has accounts for. */
    private val taken = mutableSetOf<String>()

    /** Answers every try with [answer], and keeps a try unfinished as the app does: until the server turns the name down. */
    private inner class Account(private var unfinished: String?, private val answer: RegisterResult) : NewAccount {
        val tries = mutableListOf<String>()

        override val address = ServerAddress.DEFAULT

        override fun unfinishedName() = unfinished

        override suspend fun check(name: String) =
            if (name in taken) UsernameCheck(false, UsernameVerdict.TAKEN, name) else UsernameCheck(true, UsernameVerdict.FREE, name)

        override suspend fun create(name: String): RegisterResult {
            tries += name
            unfinished = name.takeUnless { answer == RegisterResult.NameTaken }
            // The server made the account, and its answer was lost on the way back.
            if (answer == ServerTrouble.Unreachable) taken += name
            return answer
        }
    }

    private fun show(account: NewAccount) {
        compose.setContent {
            CompositionLocalProvider(LocalClock provides { now }) {
                BleskTheme(motion = BleskMotion(reduced = true)) {
                    UsernameRoute(account, onInviteRefused = {}, onOpen = {}, onBack = {})
                }
            }
        }
    }

    private fun type(name: String) {
        compose.onNodeWithContentDescription(FIELD).performTextReplacement(name)
        compose.mainClock.advanceTimeBy(CHECK_MILLIS)
    }

    private fun create() {
        compose.onNodeWithText(CREATE).performClick()
        compose.mainClock.advanceTimeBy(CHECK_MILLIS)
    }

    private fun waitFor(text: String) {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun aNameLeftByASignUpThatBrokeOffIsMadeThoughTheServerHasIt() {
        taken += "egor"
        val account = Account(unfinished = "egor", answer = ServerTrouble.Unreachable)
        show(account)

        type("egor")
        waitFor(YOURS)
        create()

        assertEquals(listOf("egor"), account.tries)
    }

    @Test
    fun aNameTypedAgainAfterTheServerFellSilentIsStillTheTrys() {
        val account = Account(unfinished = null, answer = ServerTrouble.Unreachable)
        show(account)
        type("egor")
        create()
        waitFor(YOURS)

        // Long after: the server is asked again, and has the account by now.
        now += Duration.ofMinutes(2)
        type("ego")
        type("egor")
        waitFor(YOURS)
        create()

        assertEquals(listOf("egor", "egor"), account.tries)
    }

    @Test
    fun aNameTheServerTurnsDownIsTakenThoughATryWasLeftWithIt() {
        taken += "egor"
        val account = Account(unfinished = "egor", answer = RegisterResult.NameTaken)
        show(account)
        type("egor")
        create()

        waitFor(EGOR_TAKEN)
        type("ego")
        type("egor")
        waitFor(EGOR_TAKEN)
        create()

        assertEquals(listOf("egor"), account.tries)
    }

    private companion object {
        const val FIELD = "Имя пользователя"
        const val CREATE = "Создать аккаунт"
        const val YOURS = "Это твоё имя"
        const val EGOR_TAKEN = "@egor уже занято"
        const val CHECK_MILLIS = 1_000L
        const val WAIT_MILLIS = 5_000L
    }
}
