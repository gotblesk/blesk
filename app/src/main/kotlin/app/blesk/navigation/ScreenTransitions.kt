package app.blesk.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import app.blesk.core.design.motion.LocalScreenMoving
import app.blesk.core.design.motion.LocalScreenScope
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.BleskMotion
import app.blesk.core.motion.Durations
import app.blesk.core.motion.Viscous

/**
 * One screen of the «стопка»: opaque on its own, darkened while it lies under the screen above
 * it, and still inside while it moves ([LocalScreenMoving]): loops hold, lenses turn matte, so a
 * screen in a transition is only shifted, never redrawn.
 *
 * [forward] tells which way the stack is going: deeper, the screen going under darkens; back, the
 * screen coming out from under starts dark and clears.
 */
@Composable
internal fun Screen(forward: () -> Boolean, content: @Composable () -> Unit) {
    val scope = LocalNavAnimatedContentScope.current
    val transition = scope.transition
    val dim = transition.animateFloat(transitionSpec = { tween(Durations.TRANSITION, easing = Viscous) }, label = "dim") {
        when (it) {
            EnterExitState.PreEnter -> if (forward()) 0f else UNDER_DIM
            EnterExitState.Visible -> 0f
            EnterExitState.PostExit -> if (forward()) UNDER_DIM else 0f
        }
    }
    val moving = remember(transition) { { transition.currentState != transition.targetState } }
    val background = Blesk.colors.background
    CompositionLocalProvider(LocalScreenScope provides scope, LocalScreenMoving provides moving) {
        Box(
            Modifier
                .fillMaxSize()
                .background(background)
                .drawWithContent {
                    drawContent()
                    if (dim.value > 0f) drawRect(Color.Black.copy(alpha = dim.value))
                },
        ) { content() }
    }
}

/** Deeper: the new screen comes in from the right over the old one, which slides a quarter left and darkens. */
internal fun AnimatedContentTransitionScope<*>.screenEnter(motion: BleskMotion): ContentTransform =
    if (motion.reduced) {
        calm()
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(stackTween()) { width -> width },
            initialContentExit = slideOutHorizontally(stackTween()) { width -> -(width * UNDER_SHIFT).toInt() },
        )
    }

/** Back: the top screen goes off to the right and the one under it comes out from a quarter left, clearing. */
internal fun AnimatedContentTransitionScope<*>.screenBack(motion: BleskMotion): ContentTransform =
    if (motion.reduced) {
        calm()
    } else {
        ContentTransform(
            targetContentEnter = slideInHorizontally(stackTween()) { width -> -(width * UNDER_SHIFT).toInt() },
            initialContentExit = slideOutHorizontally(stackTween()) { width -> width },
            targetContentZIndex = -1f,
        )
    }

/** With reduced motion nothing travels: the screens swap in a short dissolve. */
private fun calm() = ContentTransform(fadeIn(tween(Durations.DISSOLVE)), fadeOut(tween(Durations.DISSOLVE)))

private fun <T> stackTween() = tween<T>(Durations.TRANSITION, easing = Viscous)

private const val UNDER_SHIFT = 0.25f
private const val UNDER_DIM = 0.3f
