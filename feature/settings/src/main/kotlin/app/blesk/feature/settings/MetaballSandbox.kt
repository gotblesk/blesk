package app.blesk.feature.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.metaball.Blob
import app.blesk.core.design.metaball.metaballs
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The easter egg behind a long press on the logo: drops of the theme's accent that stretch, stick
 * and part under the finger, then flow back to their circle. Pure play, nothing is saved.
 */
@Composable
fun MetaballSandbox(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val radius = with(density) { DropRadius.toPx() }
    val ring = with(density) { RingRadius.toPx() }
    val drops = remember { List(DROPS) { Animatable(Offset.Zero, Offset.VectorConverter) } }
    val homes = remember { Array(DROPS) { Offset.Zero } }
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val grip = remember { Grip() }
    val hint = stringResource(R.string.sandbox_hint)

    fun placeHomes(size: IntSize) {
        val center = Offset(size.width / 2f, size.height / 2f)
        repeat(DROPS) { i ->
            val angle = 2 * PI * i / DROPS
            homes[i] = center + Offset((cos(angle) * ring).toFloat(), (sin(angle) * ring).toFloat())
            scope.launch { drops[i].snapTo(homes[i]) }
        }
    }

    fun touchingAny(index: Int): Boolean = drops.indices.any { other ->
        other != index && (drops[other].value - drops[index].value).getDistance() < radius * 2.2f
    }

    LensScaffold(
        modifier = modifier,
        top = { TopLens(title = stringResource(R.string.sandbox_title), onBack = onBack) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged(::placeHomes)
                .semantics { contentDescription = hint }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { at ->
                            val nearest = drops.indices.minBy { (drops[it].value - at).getDistance() }
                            grip.held = nearest.takeIf { (drops[it].value - at).getDistance() < radius * 3 } ?: -1
                            if (grip.held >= 0) haptics.detach()
                            grip.touching = grip.held >= 0 && touchingAny(grip.held)
                        },
                        onDragEnd = {
                            val released = grip.held
                            grip.held = -1
                            if (released >= 0) {
                                scope.launch {
                                    drops[released].animateTo(homes[released], spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
                                }
                            }
                        },
                    ) { change, amount ->
                        val index = grip.held
                        if (index < 0) return@detectDragGestures
                        change.consume()
                        scope.launch { drops[index].snapTo(drops[index].value + amount) }
                        val now = touchingAny(index)
                        if (now && !grip.touching) haptics.stick()
                        grip.touching = now
                    }
                }
                .metaballs(Blesk.colors.accent, smoothness = 28.dp) {
                    drops.map { Blob.circle(it.value.x, it.value.y, radius) }
                },
        ) {
            Text(
                text = stringResource(R.string.sandbox_hint),
                style = Blesk.type.label,
                color = Blesk.colors.textTertiary,
                modifier = Modifier.align(Alignment.BottomCenter).padding(padding).padding(bottom = 24.dp),
            )
        }
    }
}

/** Which drop the finger holds, and whether it touches another one. */
private class Grip {
    var held = -1
    var touching = false
}

private const val DROPS = 6
private val DropRadius = 34.dp
private val RingRadius = 92.dp
