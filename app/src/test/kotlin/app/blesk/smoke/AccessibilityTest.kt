package app.blesk.smoke

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.blesk.MainActivity
import app.blesk.screenshots.FrozenApplication
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every control on the welcome screen and the main screens says what it is to TalkBack and is big
 * enough to hit: a label (text or description) and at least 48dp on its shorter side.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h860dp-xxhdpi", application = FrozenApplication::class)
class AccessibilityTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val problems = mutableListOf<String>()

    private fun check(screen: String) {
        compose.waitForIdle()
        val nodes: SemanticsNodeInteractionCollection = compose.onAllNodes(hasClickAction())
        val density = compose.density
        nodes.fetchSemanticsNodes().forEach { node ->
            val config = node.config
            // A link inside a sentence is read with its text and, per WCAG 2.5.8, has no minimum size.
            if (node.parent?.config?.contains(SemanticsProperties.Text) == true && SemanticsProperties.Role !in config) return@forEach
            val label = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
                config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() +
                config.getOrNull(SemanticsActions.OnClick)?.label.orEmpty() +
                node.children.joinToString { child -> child.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() }
            val shorter = with(density) { minOf(node.size.width, node.size.height).toDp() }
            if (label.isBlank()) problems += "$screen: unlabeled control at ${node.boundsInRoot}"
            // Reaction chips sit inline under a bubble; the same reactions are in the long-press menu
            // at full size, so for them the WCAG 2.5.8 minimum of 24dp applies.
            val minimum = if (label.startsWith(REACTION)) MIN_INLINE else MIN_TOUCH
            if (shorter < minimum) problems += "$screen: '$label' is ${shorter.value}dp"
        }
    }

    @Test
    fun controlsAreLabelledAndBigEnough() {
        compose.waitUntil(WAIT_MILLIS) { compose.onAllNodesWithText("Создать аккаунт").fetchSemanticsNodes().isNotEmpty() }
        check("welcome")
        compose.enterDemo()
        check("chats")
        compose.onNodeWithText("Аня Соколова").performClick()
        check("conversation")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Новый чат").performClick()
        check("new chat")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Комнаты").performClick()
        check("rooms")
        compose.onNodeWithContentDescription("Настройки").performClick()
        check("settings")
        compose.onNodeWithText("Оформление").performClick()
        check("appearance")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithText("Приватность и безопасность").performClick()
        check("privacy")
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Контакты").performClick()
        check("contacts")
        compose.onNodeWithContentDescription("Ты").performClick()
        check("you")
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    private companion object {
        val MIN_TOUCH = 48.dp
        val MIN_INLINE = 24.dp
        const val REACTION = "реакция"
        const val WAIT_MILLIS = 5_000L
    }
}
