package app.blesk.shared.appearance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Everything the user can change about how blesk looks. Lives in the shared module because a
 * theme can be saved to a file and sent to a chat, so its format is part of the protocol.
 */
@Serializable
data class Appearance(
    val theme: ThemeSpec = ThemeSpec.Dark,
    /** 0 is fully matte lenses, 1 is full refraction. */
    val glassStrength: Float = 1f,
    /** 0 hides glints, 1 is the default intensity. */
    val glintStrength: Float = 1f,
    val tiltResponse: Boolean = true,
    val textScale: Float = 1f,
    val density: UiDensity = UiDensity.Comfortable,
) {
    fun normalized() = copy(
        theme = theme.normalized(),
        glassStrength = glassStrength.coerceIn(0f, 1f),
        glintStrength = glintStrength.coerceIn(0f, 1f),
        textScale = textScale.coerceIn(MIN_TEXT_SCALE, MAX_TEXT_SCALE),
    )

    companion object {
        const val MIN_TEXT_SCALE = 0.85f
        const val MAX_TEXT_SCALE = 1.3f
    }
}

@Serializable
enum class UiDensity {
    @SerialName("compact")
    Compact,

    @SerialName("comfortable")
    Comfortable,
}

@Serializable
enum class ThemePreset {
    @SerialName("light")
    Light,

    @SerialName("dark")
    Dark,

    @SerialName("lilac")
    Lilac,

    @SerialName("lime")
    Lime,

    @SerialName("custom")
    Custom,
}

/**
 * A theme is a background colour and a light colour, both in OKLCH. Every other token is
 * derived from these two, so a custom theme is just different numbers.
 */
@Serializable
data class ThemeSpec(
    val preset: ThemePreset,
    val backgroundLightness: Float,
    val backgroundChroma: Float,
    val backgroundHue: Float,
    val lightLightness: Float,
    val lightChroma: Float,
    val lightHue: Float,
    /** Whether active elements glow in the light colour rather than in the text colour. */
    val accentFromLight: Boolean = false,
) {
    val isDark: Boolean get() = backgroundLightness < DARK_THRESHOLD

    /**
     * Mid-grey backgrounds cannot carry readable text in either direction, so a background is
     * either clearly dark or clearly light.
     */
    fun normalized() = copy(
        backgroundLightness = backgroundLightness.coerceIn(MIN_BACKGROUND_L, MAX_BACKGROUND_L).let { l ->
            if (l < DARK_THRESHOLD) l.coerceAtMost(MAX_DARK_BACKGROUND_L) else l.coerceAtLeast(MIN_LIGHT_BACKGROUND_L)
        },
        backgroundChroma = backgroundChroma.coerceIn(0f, MAX_BACKGROUND_CHROMA),
        backgroundHue = backgroundHue.mod(FULL_TURN),
        lightLightness = lightLightness.coerceIn(MIN_LIGHT_L, 1f),
        lightChroma = lightChroma.coerceIn(0f, MAX_LIGHT_CHROMA),
        lightHue = lightHue.mod(FULL_TURN),
    )

    companion object {
        private const val DARK_THRESHOLD = 0.6f
        private const val FULL_TURN = 360f
        const val MIN_BACKGROUND_L = 0.12f
        const val MAX_BACKGROUND_L = 0.97f
        const val MAX_DARK_BACKGROUND_L = 0.4f
        const val MIN_LIGHT_BACKGROUND_L = 0.8f
        const val MAX_BACKGROUND_CHROMA = 0.12f
        const val MIN_LIGHT_L = 0.6f
        const val MAX_LIGHT_CHROMA = 0.25f

        /** Warm light grey rather than white, otherwise a white glint has nothing to stand out from. */
        val Light = ThemeSpec(ThemePreset.Light, 0.928f, 0.007f, 80f, 1f, 0f, 0f)

        /** Graphite rather than black. */
        val Dark = ThemeSpec(ThemePreset.Dark, 0.215f, 0.008f, 265f, 1f, 0f, 0f)

        /** Muted lilac, flat, with pale lilac light. */
        val Lilac = ThemeSpec(ThemePreset.Lilac, 0.34f, 0.055f, 305f, 0.9f, 0.06f, 305f)

        /** Dark background; glints, flashes and active elements glow lime. */
        val Lime = ThemeSpec(ThemePreset.Lime, 0.19f, 0.012f, 135f, 0.91f, 0.2f, 128f, accentFromLight = true)

        val presets = listOf(Light, Dark, Lilac, Lime)

        fun preset(preset: ThemePreset): ThemeSpec = presets.firstOrNull { it.preset == preset } ?: Dark
    }
}
