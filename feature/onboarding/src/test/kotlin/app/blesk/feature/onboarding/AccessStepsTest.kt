package app.blesk.feature.onboarding

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.UsernameStatus
import app.blesk.core.design.component.rememberUsernameInput
import app.blesk.core.design.theme.BleskTheme
import app.blesk.core.motion.BleskMotion
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The server, invite and username steps on the CI emulator's 320×640 dp, and what each refuses before asking the server. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-mdpi")
class AccessStepsTest {

    @get:Rule
    val compose = createComposeRule()

    private val done = mutableListOf<String>()
    private val field = TextFieldState()
    private var serverState by mutableStateOf(ServerStepState())
    private var status by mutableStateOf<UsernameStatus>(UsernameStatus.Empty)

    private var fontScale = 1f

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BleskTheme(motion = BleskMotion(reduced = true)) { content() }
            }
        }
    }

    private fun server(scanner: (@Composable ((String) -> Unit) -> Unit)? = null, proxy: (@Composable () -> Unit)? = null) = show {
        ServerStepScreen(
            address = field,
            state = serverState,
            actions = ServerStepActions(onDone = { done += "done" }, onBack = {}, onBlesk = { done += "blesk" }),
            scanner = scanner,
            proxy = proxy,
        )
    }

    private fun invite(required: Boolean = true) = show {
        InviteStepScreen(field, InviteStepState(required = required), InviteStepActions(onNext = { done += "next:$it" }, onBack = {}))
    }

    private fun username(state: UsernameStepState = UsernameStepState()) = show {
        UsernameStepScreen(
            input = rememberUsernameInput("egor"),
            status = status,
            state = state,
            actions = UsernameStepActions(
                onCreate = { done += "create" },
                onBack = {},
                onTerms = { done += "terms" },
                onPolicy = { done += "policy" },
            ),
        )
    }

    private fun assertTypedWhereTheSmokeTestTypes(label: String) {
        val share = compose.heightShare(compose.onNodeWithContentDescription(label))
        assertTrue("$label at $share of the height", share in SmokeBand)
    }

    private fun assertAtTheBottom(text: String) {
        val share = compose.heightShare(compose.onNodeWithText(text))
        assertTrue("$text at $share of the height", share > SmokeBand.endInclusive)
    }

    private fun assertReachable(screen: String) {
        val problems = compose.unreachableControls(screen)
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun theAddressIsTypedInTheMiddleAndCheckedAtTheBottom() {
        serverState = ServerStepState(last = KnownServer("chat.example.ru", LINK), custom = true)
        server(scanner = {}, proxy = {})
        assertTypedWhereTheSmokeTestTypes("Адрес сервера")
        assertAtTheBottom("Готово")
        assertReachable("server")
    }

    @Test
    fun theKeyboardsDoneChecksTheAddress() {
        server()
        compose.onNodeWithContentDescription("Адрес сервера").performImeAction()
        compose.onNodeWithText("Готово").performClick()
        assertEquals(listOf("done", "done"), done)
    }

    @Test
    fun aScannedCodeFillsTheAddress() {
        server(scanner = { onCode -> Button("Код на камере", onClick = { onCode(LINK) }) })
        compose.onNodeWithText("Код на камере").assertDoesNotExist()
        compose.onNodeWithText("Сканировать QR").performClick()
        compose.onNodeWithText("Код на камере").performClick()
        compose.onNodeWithContentDescription("Адрес сервера").assertTextEquals(LINK)
        compose.onNodeWithText("Код на камере").assertDoesNotExist()
    }

    @Test
    fun theLastServerComesBackWithItsKey() {
        serverState = ServerStepState(last = KnownServer("chat.example.ru", LINK))
        server()
        compose.onNodeWithText("Последний: chat.example.ru").performClick()
        compose.onNodeWithContentDescription("Адрес сервера").assertTextEquals(LINK)
    }

    @Test
    fun aLongServerNameIsShownWholeInLargeType() {
        fontScale = LARGE_FONT
        serverState = ServerStepState(last = KnownServer(LONG_HOST, "https://$LONG_HOST"))
        server()
        val layout = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("Последний: $LONG_HOST", useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertFalse(layout.single().cutShort())
    }

    @Test
    fun theProxyOpensUnderItsRow() {
        server(proxy = { Button("Порт прокси", onClick = {}) })
        compose.onNodeWithText("Порт прокси").assertDoesNotExist()
        compose.onNodeWithText("Прокси").performClick()
        compose.onNodeWithText("Порт прокси").assertExists()
    }

    @Test
    fun blesksOwnServerIsOfferedOnlyFromAnother() {
        server()
        compose.onNodeWithText("Вернуться на сервер blesk").assertDoesNotExist()
        serverState = ServerStepState(custom = true)
        compose.onNodeWithText("Вернуться на сервер blesk").performClick()
        assertEquals(listOf("blesk"), done)
    }

    @Test
    fun theInviteStepIsCalledAsTheBriefCallsIt() {
        invite()
        compose.onNode(hasText("Инвайт-код") and isHeading()).assertExists()
        compose.onNodeWithContentDescription(INVITE_FIELD).assertExists()
    }

    @Test
    fun theCodeIsTypedInTheMiddleAndSentAtTheBottom() {
        invite()
        assertTypedWhereTheSmokeTestTypes(INVITE_FIELD)
        assertAtTheBottom("Дальше")
        assertReachable("invite")
    }

    @Test
    fun aPastedInviteLinkBecomesItsCode() {
        invite()
        compose.onNodeWithContentDescription(INVITE_FIELD).performTextReplacement("Заходи: https://blesk.su/i/k7m2p9xq4tzd")
        compose.onNodeWithContentDescription(INVITE_FIELD).assertTextEquals(CODE)
    }

    @Test
    fun aCodeTypedInCapitalsBecomesSmall() {
        invite()
        compose.onNodeWithContentDescription(INVITE_FIELD).performTextInput("K7M2")
        compose.onNodeWithContentDescription(INVITE_FIELD).assertTextEquals("k7m2")
    }

    @Test
    fun pasteTakesTheCodeOutOfTheClipboardMessage() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val message = ClipData.newPlainText("", "Заходи в blesk: https://blesk.su/i/$CODE")
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(message)
        invite()
        compose.onNodeWithText("Вставить").performClick()
        compose.onNodeWithContentDescription(INVITE_FIELD).assertTextEquals(CODE)
    }

    @Test
    fun aBadCodeIsRefusedBeforeTheServer() {
        invite()
        compose.onNodeWithContentDescription(INVITE_FIELD).performTextInput("abc")
        compose.onNodeWithText("Дальше").performClick()
        assertEquals(emptyList<String>(), done)
        compose.onNodeWithText(INVALID).assertExists()
        compose.onNodeWithContentDescription(INVITE_FIELD).performTextInput("d")
        compose.onNodeWithText(INVALID).assertDoesNotExist()
    }

    @Test
    fun noCodeIsRefusedWhereInvitesAreNeeded() {
        invite(required = true)
        compose.onNodeWithText("Дальше").performClick()
        assertEquals(emptyList<String>(), done)
        compose.onNodeWithText("Нужен инвайт: попроси ссылку у того, кто уже в blesk").assertExists()
    }

    @Test
    fun noCodeGoesOnWhereInvitesAreNotNeeded() {
        invite(required = false)
        compose.onNodeWithText(OPTIONAL).assertExists()
        compose.onNodeWithText("Дальше").performClick()
        assertEquals(listOf("next:null"), done)
    }

    @Test
    fun aRightCodeGoesOn() {
        invite()
        compose.onNodeWithContentDescription(INVITE_FIELD).performTextInput(CODE)
        compose.onNodeWithContentDescription(INVITE_FIELD).performImeAction()
        assertEquals(listOf("next:$CODE"), done)
    }

    @Test
    fun theNameIsTypedInTheMiddleAndTheAccountCreatedAtTheBottom() {
        status = UsernameStatus.Free("egor")
        username()
        assertTypedWhereTheSmokeTestTypes("Имя пользователя")
        assertAtTheBottom("Создать аккаунт")
        assertReachable("username")
    }

    @Test
    fun aNameKnownNotToWorkIsNotSentToTheServer() {
        username()
        listOf(
            UsernameStatus.Empty,
            UsernameStatus.Problem(app.blesk.shared.protocol.UsernameProblem.TooShort),
            UsernameStatus.Taken("egor", persistentListOf("egor27")),
            UsernameStatus.Reserved("admin", persistentListOf()),
        ).forEach { refused ->
            status = refused
            compose.onNodeWithText("Создать аккаунт").performClick()
        }
        assertEquals(emptyList<String>(), done)
        compose.onNodeWithContentDescription("Имя пользователя").assert(SemanticsMatcher.expectValue(SemanticsProperties.Focused, true))
    }

    @Test
    fun aNameFreeOrNotYetKnownIsSent() {
        username()
        listOf(UsernameStatus.Free("egor"), UsernameStatus.Checking, UsernameStatus.Unknown).forEach { open ->
            status = open
            compose.onNodeWithText("Создать аккаунт").performClick()
        }
        status = UsernameStatus.Free("egor")
        compose.onNodeWithContentDescription("Имя пользователя").performImeAction()
        assertEquals(List(4) { "create" }, done)
    }

    @Test
    fun whileTheAccountIsCreatedTheButtonWaits() {
        status = UsernameStatus.Free("egor")
        username(UsernameStepState(busy = true))
        compose.onNodeWithText("Создать аккаунт").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Подожди"))
        compose.onNodeWithText("Создать аккаунт").performClick()
        compose.onNodeWithContentDescription("Имя пользователя").performImeAction()
        assertEquals(emptyList<String>(), done)
    }

    @Test
    fun theRulesAndThePolicyOpenFromTheirWords() {
        username()
        tapWord(TERMS, "правила")
        tapWord(TERMS, "политику конфиденциальности")
        assertEquals(listOf("terms", "policy"), done)
    }

    /** Taps the middle of [word] inside the text [line], where a link in a sentence is. */
    private fun tapWord(line: String, word: String) {
        val node = compose.onNodeWithText(line)
        val layout = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        val start = line.indexOf(word)
        val box = layout.single().getBoundingBox(start + word.length / 2)
        node.performTouchInput { click(box.center) }
    }

    private companion object {
        const val INVITE_FIELD = "Код или ссылка"
        const val LONG_HOST = "messenger.my-company-name.example.org"
        const val LARGE_FONT = 1.3f
        const val CODE = "k7m2p9xq4tzd"
        const val TERMS = "Создавая аккаунт, ты принимаешь правила и политику конфиденциальности"
        const val LINK = "blesk://server?url=https://chat.example.ru&pin=sha256/AbC+dE/f="
        const val INVALID = "Инвайт не подошёл: он уже использован, истёк или набран с ошибкой"
        const val OPTIONAL = "Этот сервер может пускать и без приглашения: если кода нет, оставь поле пустым"
    }
}
