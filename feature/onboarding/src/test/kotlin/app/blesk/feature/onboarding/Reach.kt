package app.blesk.feature.onboarding

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Where a control sits on the screen, from 0 at the top to 1 at the bottom. The smoke test on the
 * CI emulator (320×640 dp) taps only what lies between [SmokeBand] and, with `ui tab`, what lies
 * below it.
 */
internal fun ComposeContentTestRule.heightShare(node: SemanticsNodeInteraction): Float {
    val bounds = node.getUnclippedBoundsInRoot()
    val screen = onRoot().getUnclippedBoundsInRoot().bottom
    return ((bounds.top + bounds.bottom) / 2f) / screen
}

internal val SmokeBand = 0.15f..0.78f

/**
 * Every control says what it is to TalkBack and is big enough to hit: a label (text or
 * description) and at least 48 dp on its shorter side. A link inside a sentence is read with its
 * text and, per WCAG 2.5.8, has no minimum size.
 */
internal fun ComposeContentTestRule.unreachableControls(screen: String): List<String> {
    waitForIdle()
    val problems = mutableListOf<String>()
    onAllNodes(hasClickAction()).fetchSemanticsNodes().forEach { node ->
        val config = node.config
        if (node.parent?.config?.contains(SemanticsProperties.Text) == true && SemanticsProperties.Role !in config) return@forEach
        val label = config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
            config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() +
            config.getOrNull(SemanticsActions.OnClick)?.label.orEmpty() +
            node.children.joinToString { child -> child.config.getOrNull(SemanticsProperties.Text)?.joinToString().orEmpty() }
        val shorter: Dp = with(density) { minOf(node.size.width, node.size.height).toDp() }
        if (label.isBlank()) problems += "$screen: unlabeled control at ${node.boundsInRoot}"
        if (shorter < MinTouch) problems += "$screen: '$label' is ${shorter.value}dp"
    }
    return problems
}

private val MinTouch = 48.dp
