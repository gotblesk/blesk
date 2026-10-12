package app.blesk.core.design.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

private val LocalBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** How a lens is rendered on this device. */
enum class GlassMode { Refraction, Blur, Matte }

fun glassModeFor(sdk: Int = Build.VERSION.SDK_INT, strength: Float): GlassMode = when {
    strength <= 0f -> GlassMode.Matte
    sdk >= Build.VERSION_CODES.TIRAMISU -> GlassMode.Refraction
    sdk >= Build.VERSION_CODES.S -> GlassMode.Blur
    else -> GlassMode.Matte
}

/**
 * A screen with the only two pieces of glass in blesk: the top lens and the bottom lens.
 * [content] is drawn once into a backdrop layer that the lenses refract, and receives
 * padding equal to the lenses so nothing important hides under them.
 */
@Composable
fun LensScaffold(
    modifier: Modifier = Modifier,
    background: Color = Blesk.colors.background,
    underlay: @Composable BoxScope.() -> Unit = {},
    top: @Composable () -> Unit = {},
    bottom: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val backdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    var topHeight by remember { mutableIntStateOf(0) }
    var bottomHeight by remember { mutableIntStateOf(0) }
    Box(modifier.fillMaxSize().background(background)) {
        Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
            Box(Modifier.fillMaxSize().background(background))
            underlay()
            val padding = with(density) { PaddingValues(top = topHeight.toDp(), bottom = bottomHeight.toDp()) }
            content(padding)
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().onSizeChanged { topHeight = it.height }) { top() }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { bottomHeight = it.height }) { bottom() }
        }
    }
}

/**
 * Turns a capsule into a lens over the [LensScaffold] content: refraction on Android 13+,
 * blur on 12, matte below and when the glass strength is set to zero.
 */
@Composable
fun Modifier.lens(shape: Shape, elevation: Dp = 18.dp, refract: Boolean = true): Modifier {
    val backdrop = LocalBackdrop.current
    val strength = Blesk.appearance.glassStrength
    val colors = Blesk.colors
    val mode = glassModeFor(strength = strength)
    if (backdrop == null || mode == GlassMode.Matte) {
        return this.clip(shape).background(colors.surface)
    }
    val tint = colors.glassTint.copy(alpha = colors.glassTint.alpha * (1f - 0.45f * strength))
    val shadowColor = Color.Black.copy(alpha = if (colors.isDark) 0.28f else 0.10f)
    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            vibrancy()
            blur((if (mode == GlassMode.Refraction) 6.dp else 14.dp).toPx() * strength)
            if (mode == GlassMode.Refraction && refract) {
                lens(refractionHeight = 14.dp.toPx() * strength, refractionAmount = 28.dp.toPx() * strength)
            }
        },
        highlight = null,
        shadow = { Shadow(radius = elevation, offset = DpOffset(0.dp, elevation / 4), color = shadowColor) },
        onDrawSurface = { drawRect(tint) },
    )
}
