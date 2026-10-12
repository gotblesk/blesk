package app.blesk.smoke

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Walks the real activity through the main demo flow. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi")
class MainFlowTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun demoChatThemesAndDebug() {
        compose.onNodeWithContentDescription("Новый чат").assertIsDisplayed()

        compose.onNodeWithText("Эхо").performClick()
        compose.onNodeWithContentDescription("Сообщение").performTextInput("проверка")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithText("проверка").performTouchInput { longClick() }
        compose.onNodeWithText("Выбрать").performClick()
        compose.onNodeWithText("Выбрано 1").assertIsDisplayed()
        compose.onNodeWithContentDescription("Отменить").performClick()
        compose.onNodeWithContentDescription("Эмодзи").performClick()
        compose.onNodeWithContentDescription("Найти эмодзи").performTextInput("ржака")
        compose.waitUntil(5_000) { compose.onAllNodesWithContentDescription("смеется до слез").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("смеется до слез").performClick()
        compose.onNodeWithContentDescription("Клавиатура").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithContentDescription("Поиск").performClick()
        compose.onNodeWithContentDescription("Чат, человек или слово").performTextInput("проверка")
        compose.mainClock.advanceTimeBy(1_000)
        compose.waitForIdle()
        compose.onNodeWithText("Ты: проверка", substring = true).assertExists()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("Миша Ковалёв").performTouchInput { longClick() }
        compose.onNodeWithText("В архив").performClick()
        compose.onNodeWithText("Архив").performClick()
        compose.onNodeWithText("Миша Ковалёв").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithContentDescription("Новый чат").performClick()
        compose.onNodeWithText("Лера").performClick()
        compose.onNodeWithText("защищено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("Комнаты").performClick()
        compose.onNodeWithText("Студия").assertIsDisplayed()

        compose.onNodeWithText("Ты").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Лаймовая").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("Ключи и безопасность").performClick()
        compose.onNodeWithText("Запрет скриншотов").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()

        // The row may sit under the bottom lens, so it is pressed through its click action, not a touch.
        compose.onNodeWithText("О приложении").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Лицензии").performClick()
        compose.onNodeWithText("Onest").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()
        repeat(7) { compose.onNodeWithText("Версия").performClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Отладка").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Сбоев не было").assertIsDisplayed()
    }
}
