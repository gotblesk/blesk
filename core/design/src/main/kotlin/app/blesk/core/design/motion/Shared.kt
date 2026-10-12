package app.blesk.core.design.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.blesk.core.design.theme.Blesk

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
    return with(shared) { this@sharedBounds.sharedBounds(rememberSharedContentState(key), screen) }
}

/** One element that flows from its place on one screen to its place on the next, like an avatar. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElement(key: String): Modifier {
    val shared = LocalSharedScope.current ?: return this
    val screen = LocalScreenScope.current ?: return this
    if (Blesk.motion.reduced) return this
    return with(shared) { this@sharedElement.sharedElement(rememberSharedContentState(key), screen) }
}
