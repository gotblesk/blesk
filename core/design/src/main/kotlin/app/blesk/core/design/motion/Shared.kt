package app.blesk.core.design.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.Durations
import app.blesk.core.motion.Viscous

/** The scope shared elements travel in, set once around the navigation. */
val LocalSharedScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** The enter and exit of the current screen, set by the navigation for each screen. */
val LocalScreenScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * The bounds of something that turns into a whole screen and back: a chat row and its chat.
 * Both ends use the same [key]. Without animations it does nothing.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedBounds(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val screen = LocalScreenScope.current ?: return this
    if (Blesk.motion.reduced) return this
    val config = remember(screen) { FollowsGesture(screen) }
    // The cross-fade of shared bounds runs whenever both ends exist, even while they are held in
    // place; during the back gesture it would make the leaving screen see-through.
    val seeking = screen.transition.isSeeking
    return with(shared) {
        this@sharedBounds.sharedBounds(
            rememberSharedContentState(key, config),
            screen,
            enter = if (seeking) EnterTransition.None else fadeIn(SharedTween()),
            exit = if (seeking) ExitTransition.None else fadeOut(SharedTween()),
            boundsTransform = { _, _ -> SharedTween() },
        )
    }
}

/** One element that flows from its place on one screen to its place on the next, like an avatar. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val screen = LocalScreenScope.current ?: return this
    if (Blesk.motion.reduced) return this
    val config = remember(screen) { FollowsGesture(screen) }
    return with(shared) {
        this@sharedElement.sharedElement(rememberSharedContentState(key, config), screen, boundsTransform = { _, _ -> SharedTween() })
    }
}

/**
 * Shared elements sit still for the whole back gesture, which Navigation Compose drives by
 * seeking until the screen is gone: the screen only shrinks under the finger, nothing flies.
 * Every other way back (the button, a tap on «назад») lets them flow into place.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
private class FollowsGesture(private val screen: AnimatedVisibilityScope) : SharedTransitionScope.SharedContentConfig {
    override val SharedTransitionScope.SharedContentState.isEnabled: Boolean get() = !screen.transition.isSeeking
}

/**
 * Shared elements move exactly as long as the screen change they belong to: a longer spring would
 * stretch the transition the back gesture seeks through, and the screen would fade too early.
 */
@Suppress("FunctionName")
private fun <T> SharedTween() = tween<T>(Durations.TRANSITION, easing = Viscous)

/** The shared key that ties a settings row to the screen it opens. */
fun screenKey(name: String) = "screen:$name"
