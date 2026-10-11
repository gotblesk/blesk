package app.blesk.core.design.spark

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * The spark: the theme's light flares up under the finger and fades when it lifts.
 * This replaces ripples everywhere through LocalIndication.
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
    private var origin = Offset.Unspecified

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        origin = interaction.pressPosition
                        launch { flare(1f) }
                    }
                    is PressInteraction.Release, is PressInteraction.Cancel -> launch { flare(0f) }
                }
            }
        }
    }

    private suspend fun flare(target: Float) {
        if (!animate) {
            glow.snapTo(target)
        } else if (target > 0f) {
            glow.animateTo(target, tween(durationMillis = 90))
        } else {
            glow.animateTo(target, spring(dampingRatio = 1f, stiffness = 250f))
        }
        invalidateDraw()
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        val amount = glow.value * strength
        if (amount <= 0.001f) return
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
