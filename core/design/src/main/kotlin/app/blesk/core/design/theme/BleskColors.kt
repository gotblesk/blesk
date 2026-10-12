package app.blesk.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.blesk.core.design.color.ContrastLevel
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.color.ensureContrast
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Colour tokens. The interface is almost colourless: [light] is the only colour that carries
 * meaning (protection glints, event flashes, touch response), and [spectrum] exists only for
 * the short flash that marks an event.
 */
@Immutable
data class BleskColors(
    val isDark: Boolean,
    val background: Color,
    /** Matte surfaces: rows, cards, sheets. */
    val surface: Color,
    val surfaceStrong: Color,
    /** Tint laid over the two glass lenses. */
    val glassTint: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    /** The light of the theme. Glints and the spark are drawn in it. */
    val light: Color,
    /** Active controls: selected tab, toggles, primary button. */
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val spectrum: ImmutableList<Color>,
)

object BleskPalette {

    fun from(spec: ThemeSpec): BleskColors {
        val theme = spec.normalized()
        val dark = theme.isDark
        val bg = Oklch(theme.backgroundLightness, theme.backgroundChroma, theme.backgroundHue)
        val background = bg.toColor()
        val step = if (dark) 1f else -1f
        val surface = bg.withL(bg.l + step * SURFACE_STEP).withC(bg.c * SURFACE_CHROMA).toColor()
        val surfaceStrong = bg.withL(bg.l + step * SURFACE_STRONG_STEP).withC(bg.c * SURFACE_CHROMA).toColor()

        val textHue = bg.h
        val textChroma = (bg.c * TEXT_CHROMA_SHARE).coerceAtMost(MAX_TEXT_CHROMA)
        val primaryRaw = Oklch(if (dark) 0.96f else 0.25f, textChroma, textHue).toColor()
        val secondaryRaw = Oklch(if (dark) 0.78f else 0.44f, textChroma, textHue).toColor()
        val tertiaryRaw = Oklch(if (dark) 0.62f else 0.58f, textChroma, textHue).toColor()
        val textPrimary =
            ensureReadable(primaryRaw, listOf(background, surface, surfaceStrong), minRatio = ContrastLevel.BODY_TEXT)
        val textSecondary =
            ensureReadable(secondaryRaw, listOf(background, surface, surfaceStrong), minRatio = ContrastLevel.BODY_TEXT)
        val textTertiary =
            ensureReadable(tertiaryRaw, listOf(background, surface, surfaceStrong), minRatio = ContrastLevel.LARGE_TEXT)

        val light = Oklch(theme.lightLightness, theme.lightChroma, theme.lightHue).toColor()
        val accent = if (theme.accentFromLight) {
            ensureContrast(light, background, ContrastLevel.UI_COMPONENT)
        } else {
            textPrimary
        }
        val onAccent = ensureContrast(background, accent, ContrastLevel.BODY_TEXT)
        val danger = ensureReadable(
            Oklch(if (dark) 0.72f else 0.55f, 0.17f, 25f).toColor(),
            listOf(background, surface, surfaceStrong), minRatio = ContrastLevel.BODY_TEXT,
        )

        return BleskColors(
            isDark = dark,
            background = background,
            surface = surface,
            surfaceStrong = surfaceStrong,
            glassTint = bg.withL(bg.l + step * GLASS_STEP).toColor(alpha = if (dark) 0.55f else 0.6f),
            hairline = textPrimary.copy(alpha = if (dark) 0.10f else 0.09f),
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textTertiary = textTertiary,
            light = light,
            accent = accent,
            onAccent = onAccent,
            danger = danger,
            spectrum = spectrumFor(theme, dark),
        )
    }

    private fun ensureReadable(color: Color, backgrounds: List<Color>, minRatio: Float): Color =
        backgrounds.fold(color) { acc, bg -> ensureContrast(acc, bg, minRatio) }

    /**
     * The event flash. In neutral themes it is a full but soft spectrum; in coloured themes the
     * spectrum is pulled towards the light so that the flash reads as the theme's own light.
     */
    private fun spectrumFor(theme: ThemeSpec, dark: Boolean): ImmutableList<Color> {
        val l = if (dark) 0.84f else 0.72f
        val c = if (theme.lightChroma > TINTED_LIGHT_CHROMA) 0.09f else 0.15f
        val center = theme.lightHue
        val hues = if (theme.lightChroma > TINTED_LIGHT_CHROMA) {
            listOf(center - 50f, center - 20f, center, center + 20f, center + 50f)
        } else {
            listOf(20f, 85f, 150f, 220f, 300f)
        }
        return hues.map { Oklch(l, c, it.mod(360f)).toColor() }.toImmutableList()
    }

    private const val SURFACE_STEP = 0.05f
    private const val SURFACE_STRONG_STEP = 0.1f

    /** Lenses sit between the two surfaces, so background, rows, lenses and pressed parts read as separate steps. */
    private const val GLASS_STEP = 0.075f
    private const val SURFACE_CHROMA = 0.9f
    private const val TEXT_CHROMA_SHARE = 0.35f
    private const val MAX_TEXT_CHROMA = 0.03f
    private const val TINTED_LIGHT_CHROMA = 0.03f
}
