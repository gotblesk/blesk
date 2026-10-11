package app.blesk.smoke

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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
        compose.onNodeWithText("Демо").assertIsDisplayed()

        compose.onNodeWithText("Эхо").performClick()
        compose.onNodeWithContentDescription("Сообщение").performTextInput("проверка")
        compose.onNodeWithContentDescription("Отправить").performClick()
        compose.mainClock.advanceTimeBy(5_000)
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("Контакты").performClick()
        compose.onNodeWithText("Лера").performClick()
        compose.onNodeWithText("защищено").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("Настройки").performClick()
        compose.onNodeWithText("Оформление").performClick()
        compose.onNodeWithText("Лаймовая").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()

        compose.onNodeWithText("О приложении").performClick()
        repeat(7) { compose.onNodeWithText("Версия").performClick() }
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Отладка").performClick()
        compose.onNodeWithText("Сбоев не было").assertIsDisplayed()
    }
}
