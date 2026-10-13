package app.blesk.core.design.spark

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The answer every tap target gives the finger, through LocalIndication instead of ripples:
 * it shrinks to 96 % and the theme's light shows under the finger; held on, it keeps shrinking
 * slowly towards 94 % as a long press builds; let go, it springs back and the light fades.
 */
class SparkIndication(
    private val light: Color,
    private val strength: Float,
    private val animate: Boolean,
) : IndicationNodeFactory {

    override fun create(interactionSource: InteractionSource): DelegatableNode =
        SparkNode(interactionSource, light, strength, animate)

    override fun equals(other: Any?): Boolean =
        other is SparkIndication && other.light == light && other.strength == strength && other.animate == animate

    override fun hashCode(): Int = (light.hashCode() * 31 + strength.hashCode()) * 31 + animate.hashCode()
}

private class SparkNode(
    private val interactionSource: InteractionSource,
    private val light: Color,
    private val strength: Float,
    private val animate: Boolean,
) : Modifier.Node(), DrawModifierNode {

    private val glow = Animatable(0f)
    private val press = Animatable(1f)
    private var holding: Job? = null
    private var origin = Offset.Unspecified

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        origin = interaction.pressPosition
                        launch { flare(1f) }
                        holding?.cancel()
                        holding = launch { hold() }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        holding?.cancel()
                        launch { flare(0f) }
                        launch { release() }
                    }
                }
            }
        }
    }

    private suspend fun hold() {
        if (!animate) {
            press.snapTo(PRESSED)
            return
        }
        press.animateTo(PRESSED, tween(PRESS_MILLIS))
        // A long press shows itself coming: the element goes on shrinking while it is held.
        press.animateTo(HELD, tween(HOLD_MILLIS, easing = LinearEasing))
    }

    private suspend fun release() {
        if (!animate) press.snapTo(1f) else press.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium))
    }

    private suspend fun flare(target: Float) {
        if (!animate) {
            glow.snapTo(target)
        } else if (target > 0f) {
            glow.animateTo(target, tween(durationMillis = PRESS_MILLIS))
        } else {
            glow.animateTo(target, spring(dampingRatio = 1f, stiffness = 250f))
        }
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        val scale = press.value
        scale(scale) {
            this@draw.drawContent()
            val amount = glow.value * strength
            if (amount <= 0.001f) return@scale
            val center = if (origin.isSpecified) origin else Offset(size.width / 2f, size.height / 2f)
            val radius = max(size.width, size.height) * 0.75f
            drawRect(color = light.copy(alpha = 0.06f * amount))
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(light.copy(alpha = 0.22f * amount), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
            )
        }
    }
}

private const val PRESSED = 0.96f
private const val HELD = 0.94f
private const val PRESS_MILLIS = 90
private const val HOLD_MILLIS = 400
