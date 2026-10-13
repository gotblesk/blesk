package app.blesk.core.design.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
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
 * What hangs on the lenses of every screen, whatever the screen: a call in progress under the top
 * lens, the music player on the bottom one. Each screen's lenses grow by them, so the content
 * moves aside rather than slipping under.
 */
@Immutable
class LensAccessories(val top: @Composable () -> Unit = {}, val bottom: @Composable () -> Unit = {}) {
    companion object {
        val None = LensAccessories()
    }
}

val LocalLensAccessories = staticCompositionLocalOf { LensAccessories.None }

/**
 * A screen with the only two pieces of glass in blesk: the top lens and the bottom lens.
 * [content] is drawn once into a backdrop layer that the lenses refract, and receives
 * padding equal to the lenses so nothing important hides under them.
 *
 * The lenses are measured first and the content after them in the same pass, and the padding
 * is read while measuring, not while composing: when the bottom lens rides up with the keyboard
 * the content follows in the same frame, and nothing is composed again for it.
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
    val padding = remember { LensPadding() }
    val accessories = LocalLensAccessories.current
    Layout(
        contents = listOf(
            {
                Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) {
                    Box(Modifier.fillMaxSize().background(background))
                    underlay()
                    // A scaffold inside another one (a sheet, a pane) does not repeat what hangs on the outer lenses.
                    CompositionLocalProvider(LocalLensAccessories provides LensAccessories.None) { content(padding) }
                    StatusBarScrim(background)
                }
            },
            {
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Column(Modifier.fillMaxWidth()) {
                        top()
                        accessories.top()
                    }
                }
            },
            {
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Column(Modifier.fillMaxWidth()) {
                        accessories.bottom()
                        bottom()
                    }
                }
            },
        ),
        modifier = modifier.fillMaxSize().background(background),
    ) { (contentSlot, topSlot, bottomSlot), constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val tops = topSlot.map { it.measure(loose) }
        val bottoms = bottomSlot.map { it.measure(loose) }
        padding.top.value = tops.maxOfOrNull { it.height }?.toDp() ?: 0.dp
        padding.bottom.value = bottoms.maxOfOrNull { it.height }?.toDp() ?: 0.dp
        val contents = contentSlot.map { it.measure(constraints) }
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        layout(width, height) {
            contents.forEach { it.place(0, 0) }
            tops.forEach { it.place(0, 0) }
            bottoms.forEach { it.place(0, height - it.height) }
        }
    }
}

/**
 * A soft veil of the background under the status bar: text scrolling up never reads over the
 * clock and the icons, and fades out a little below them instead of stopping at a hard edge.
 */
@Composable
private fun BoxScope.StatusBarScrim(background: Color) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    if (top == 0.dp) return
    Box(
        Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
            .height(top + ScrimFade)
            .drawBehind {
                val solid = top.toPx() / size.height
                drawRect(
                    Brush.verticalGradient(
                        0f to background.copy(alpha = SCRIM_ALPHA),
                        solid to background.copy(alpha = SCRIM_ALPHA * SCRIM_EDGE),
                        1f to background.copy(alpha = 0f),
                    ),
                )
            },
    )
}

private val ScrimFade = 16.dp
private const val SCRIM_ALPHA = 0.92f
private const val SCRIM_EDGE = 0.7f

/** Lens heights as padding whose values are read during layout, so a change never recomposes. */
@Stable
private class LensPadding : PaddingValues {
    val top = mutableStateOf(0.dp)
    val bottom = mutableStateOf(0.dp)

    override fun calculateTopPadding(): Dp = top.value

    override fun calculateBottomPadding(): Dp = bottom.value

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp = 0.dp

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp = 0.dp
}

/**
 * Turns a capsule into a lens over the [LensScaffold] content: refraction on Android 13+,
 * blur on 12, matte below and when the glass strength is set to zero.
 */
@Composable
fun Modifier.lens(shape: Shape, elevation: Dp = 18.dp, refract: Boolean = true, rim: Boolean = true): Modifier {
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
        // The rim catches the light, so a lens reads as glass even over an empty background.
        highlight = if (rim && mode == GlassMode.Refraction) ({ Highlight.Default.copy(alpha = RIM_ALPHA * strength) }) else null,
        shadow = if (elevation > 0.dp) {
            { Shadow(radius = elevation, offset = DpOffset(0.dp, elevation / 4), color = shadowColor) }
        } else {
            null
        },
        onDrawSurface = { drawRect(tint) },
    )
}

/**
 * [base]'s top and bottom grown by fixed amounts, still read when laid out: built from the lens
 * padding it keeps following the lenses without composing again. The lenses add nothing at the
 * sides, so the sides are just [start] and [end]. [keepTop] and [keepBottom] drop a side of [base].
 */
@Stable
private class ExtendedPadding(
    private val base: PaddingValues,
    private val start: Dp,
    private val top: Dp,
    private val end: Dp,
    private val bottom: Dp,
    private val keepTop: Boolean,
    private val keepBottom: Boolean,
) : PaddingValues {
    override fun calculateTopPadding(): Dp = top + if (keepTop) base.calculateTopPadding() else 0.dp

    override fun calculateBottomPadding(): Dp = bottom + if (keepBottom) base.calculateBottomPadding() else 0.dp

    override fun calculateLeftPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) start else end

    override fun calculateRightPadding(layoutDirection: LayoutDirection): Dp =
        if (layoutDirection == LayoutDirection.Ltr) end else start
}

/** These paddings plus fixed amounts, read lazily: a list under the lenses moves with them in the same frame. */
fun PaddingValues.extended(start: Dp = 0.dp, top: Dp = 0.dp, end: Dp = 0.dp, bottom: Dp = 0.dp): PaddingValues =
    ExtendedPadding(this, start, top, end, bottom, keepTop = true, keepBottom = true)

/** Only the bottom of these paddings, plus [extra]: for something that sits just above the bottom lens. */
fun PaddingValues.bottomOnly(extra: Dp = 0.dp): PaddingValues =
    ExtendedPadding(this, 0.dp, 0.dp, 0.dp, extra, keepTop = false, keepBottom = true)

/** Only the top of these paddings, plus [extra]: for something that hangs just under the top lens. */
fun PaddingValues.topOnly(extra: Dp = 0.dp): PaddingValues =
    ExtendedPadding(this, 0.dp, extra, 0.dp, 0.dp, keepTop = true, keepBottom = false)

private const val RIM_ALPHA = 0.6f
