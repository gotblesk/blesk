package app.blesk.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigationevent.NavigationEvent
import app.blesk.core.design.motion.LocalScreenMoving
import app.blesk.core.design.motion.LocalScreenScope
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.BleskMotion
import app.blesk.core.motion.Durations
import kotlin.math.roundToInt

/**
 * Every screen knows its enter and exit, so a chat row and its chat can be one shared element,
 * and a screen leaving rounds its corners as it shrinks, like a card being put back.
 *
 * While the screen moves as a whole its loops hold still ([LocalScreenMoving]). Under the back
 * gesture the leaving screen goes further: it is drawn once into a picture and only that picture
 * is scaled, so neither light, glass nor metaballs are redrawn on any frame of the gesture.
 */
@Composable
internal fun Screen(content: @Composable () -> Unit) {
    val scope = LocalNavAnimatedContentScope.current
    val transition = scope.transition
    val corner = transition.animateDp(label = "corner") { if (it == EnterExitState.Visible) 0.dp else SCREEN_CORNER }
    // The screen being returned to starts a little dim and clears as the gesture goes on.
    val dim = transition.animateFloat(label = "dim") { if (it == EnterExitState.PreEnter) UNDER_DIM else 0f }
    val moving = remember(transition) { { transition.currentState != transition.targetState } }
    val layer = rememberGraphicsLayer()
    var still by remember { mutableStateOf<ImageBitmap?>(null) }
    val leavingByGesture = transition.isSeeking && transition.targetState == EnterExitState.PostExit
    LaunchedEffect(leavingByGesture) {
        still = if (leavingByGesture && layer.size != IntSize.Zero) layer.toImageBitmap() else null
    }
    val background = Blesk.colors.background
    CompositionLocalProvider(LocalScreenScope provides scope, LocalScreenMoving provides moving) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = RoundedCornerShape(corner.value)
                    clip = true
                }
                // Every screen is opaque on its own: two screens never show through each other.
                .background(background)
                .drawWithContent {
                    val picture = still
                    if (picture != null) {
                        drawImage(picture, dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()))
                    } else {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
                    if (dim.value > 0f) drawRect(Color.Black.copy(alpha = dim.value))
                },
        ) { content() }
    }
}

/**
 * Deeper screens come out of where they were opened from. The screen below stays as it is: only
 * the new one fades in, quickly, so two half-transparent screens never lie on each other.
 */
internal fun AnimatedContentTransitionScope<*>.screenEnter(motion: BleskMotion): ContentTransform =
    ContentTransform(
        targetContentEnter = fadeIn(tween(if (motion.reduced) Durations.DISSOLVE else Durations.PRESS)) +
            scaleIn(motion.transition(), initialScale = if (motion.reduced) 1f else ENTER_SCALE),
        initialContentExit = ExitTransition.KeepUntilTransitionsFinished,
    )

/**
 * Going back, by button or by gesture. The screen below is already there, still and dimmed; the
 * leaving one stays on top and opaque while it shrinks to about 90 % with rounded corners and,
 * under the gesture, drifts with the finger away from the edge the swipe comes from. It fades
 * only at the very end. Navigation Compose plays this under the finger itself.
 */
internal fun AnimatedContentTransitionScope<*>.screenBack(motion: BleskMotion, edge: Int?): ContentTransform {
    val drift = when (edge) {
        NavigationEvent.EDGE_LEFT -> 1
        NavigationEvent.EDGE_RIGHT -> -1
        else -> 0
    }
    val exit = fadeOut(
        keyframes {
            durationMillis = Durations.TRANSITION
            1f at (Durations.TRANSITION * OPAQUE_UNTIL).toInt()
            0f at Durations.TRANSITION
        },
    ) + scaleOut(motion.transition(), targetScale = if (motion.reduced) 1f else EXIT_SCALE, transformOrigin = TransformOrigin.Center)
    val follow = if (drift == 0 || motion.reduced) {
        ExitTransition.None
    } else {
        slideOutHorizontally(motion.transition()) { width -> (width * DRIFT * drift).toInt() }
    }
    return ContentTransform(
        targetContentEnter = EnterTransition.None,
        initialContentExit = exit + follow,
        targetContentZIndex = -1f,
    )
}

private const val OPAQUE_UNTIL = 0.9f
private val SCREEN_CORNER = 28.dp
private const val ENTER_SCALE = 0.96f
private const val EXIT_SCALE = 0.9f
private const val DRIFT = 0.06f
private const val UNDER_DIM = 0.25f
