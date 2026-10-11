package app.blesk.core.design.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import app.blesk.core.design.motion.BleskMotion
import app.blesk.core.design.motion.rememberSystemMotion
import app.blesk.core.design.spark.LightSource
import app.blesk.core.design.spark.LocalLightSource
import app.blesk.core.design.spark.SparkIndication
import app.blesk.core.design.spark.rememberLightSource
import app.blesk.shared.appearance.Appearance

private val LocalColors = staticCompositionLocalOf { BleskPalette.from(Appearance().theme) }
private val LocalType = staticCompositionLocalOf { BleskType.create() }
private val LocalDimens = staticCompositionLocalOf { BleskDimens.forDensity(Appearance().density) }
private val LocalMotion = staticCompositionLocalOf { BleskMotion.Default }
private val LocalAppearance = staticCompositionLocalOf { Appearance() }

object Blesk {
    val colors: BleskColors
        @Composable @ReadOnlyComposable
        get() = LocalColors.current
    val type: BleskType
        @Composable @ReadOnlyComposable
        get() = LocalType.current
    val dimens: BleskDimens
        @Composable @ReadOnlyComposable
        get() = LocalDimens.current
    val motion: BleskMotion
        @Composable @ReadOnlyComposable
        get() = LocalMotion.current
    val appearance: Appearance
        @Composable @ReadOnlyComposable
        get() = LocalAppearance.current
    val light: LightSource
        @Composable @ReadOnlyComposable
        get() = LocalLightSource.current
}

@Composable
fun BleskTheme(
    appearance: Appearance = Appearance(),
    motion: BleskMotion = rememberSystemMotion(),
    content: @Composable () -> Unit,
) {
    val normalized = remember(appearance) { appearance.normalized() }
    val colors = remember(normalized.theme) { BleskPalette.from(normalized.theme) }
    val type = remember(normalized.textScale) { BleskType.create(normalized.textScale) }
    val dimens = remember(normalized.density) { BleskDimens.forDensity(normalized.density) }
    val lightSource = rememberLightSource(enabled = normalized.tiltResponse && !motion.reduced)
    val indication = remember(colors.light, normalized.glintStrength, motion.reduced) {
        SparkIndication(colors.light, normalized.glintStrength, animate = !motion.reduced)
    }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.3f))
    }
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides type,
        LocalDimens provides dimens,
        LocalMotion provides motion,
        LocalAppearance provides normalized,
        LocalLightSource provides lightSource,
        LocalIndication provides indication,
        LocalTextSelectionColors provides selection,
        content = content,
    )
}
