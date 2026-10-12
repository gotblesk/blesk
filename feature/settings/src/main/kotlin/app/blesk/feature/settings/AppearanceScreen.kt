package app.blesk.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.color.Oklch
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ChatBackdrop
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Slider
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.glint
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskPalette
import app.blesk.core.motion.rememberHaptics
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemePreset
import app.blesk.shared.appearance.ThemeSpec
import app.blesk.shared.appearance.UiDensity
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.roundToInt

/** Actions on the chat wallpaper and theme files; the screen itself stays free of storage code. */
class AppearanceFileActions(
    val hasWallpaper: Boolean,
    val onPickWallpaper: () -> Unit,
    val onRemoveWallpaper: () -> Unit,
    val onSaveTheme: () -> Unit,
    val onOpenTheme: () -> Unit,
    /** The chosen chat background, as [chatBackdropOf] reads it. */
    val background: HeaderStyle? = null,
    val onBackground: (HeaderStyle?) -> Unit = {},
)

@Composable
fun AppearanceScreen(
    appearance: Appearance,
    onChange: (Appearance) -> Unit,
    files: AppearanceFileActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = appearance.theme
    fun custom(transform: (ThemeSpec) -> ThemeSpec) = onChange(appearance.copy(theme = transform(theme).copy(preset = ThemePreset.Custom)))
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)

    ScrollScreen(title = stringResource(R.string.settings_appearance), onBack = onBack, modifier = modifier) {
        SectionHeader(stringResource(R.string.appearance_theme))
        ThemePicker(current = theme, onSelect = { onChange(appearance.copy(theme = it)) })

        SectionHeader(stringResource(R.string.appearance_background))
        Card(pad) {
            Slider(
                title = stringResource(R.string.appearance_hue),
                value = theme.backgroundHue,
                range = 0f..359f,
                valueLabel = stringResource(R.string.appearance_degrees, theme.backgroundHue.roundToInt()),
                track = hueTrack(theme.backgroundLightness, maxOf(theme.backgroundChroma, 0.05f)),
                onValueChange = { hue -> custom { it.copy(backgroundHue = hue, backgroundChroma = maxOf(it.backgroundChroma, 0.03f)) } },
            )
            Slider(
                title = stringResource(R.string.appearance_lightness),
                value = theme.backgroundLightness,
                range = ThemeSpec.MIN_BACKGROUND_L..ThemeSpec.MAX_BACKGROUND_L,
                onValueChange = { l -> custom { it.copy(backgroundLightness = l) } },
            )
            Slider(
                title = stringResource(R.string.appearance_saturation),
                value = theme.backgroundChroma,
                range = 0f..ThemeSpec.MAX_BACKGROUND_CHROMA,
                onValueChange = { c -> custom { it.copy(backgroundChroma = c) } },
            )
        }

        SectionHeader(stringResource(R.string.appearance_light))
        Card(pad) {
            Slider(
                title = stringResource(R.string.appearance_hue),
                value = theme.lightHue,
                range = 0f..359f,
                valueLabel = stringResource(R.string.appearance_degrees, theme.lightHue.roundToInt()),
                track = hueTrack(0.85f, 0.15f),
                onValueChange = { hue -> custom { it.copy(lightHue = hue, lightChroma = maxOf(it.lightChroma, 0.08f)) } },
            )
            Slider(
                title = stringResource(R.string.appearance_saturation),
                value = theme.lightChroma,
                range = 0f..ThemeSpec.MAX_LIGHT_CHROMA,
                onValueChange = { c -> custom { it.copy(lightChroma = c) } },
            )
            ToggleRow(
                title = stringResource(R.string.appearance_accent_light),
                checked = theme.accentFromLight,
                onCheckedChange = { on -> custom { it.copy(accentFromLight = on) } },
            )
        }

        SectionHeader(stringResource(R.string.appearance_glass))
        Card(pad) {
            Slider(
                title = stringResource(R.string.appearance_glass_strength),
                value = appearance.glassStrength,
                valueLabel = if (appearance.glassStrength <= 0f) {
                    stringResource(R.string.appearance_glass_matte)
                } else {
                    "${(appearance.glassStrength * 100).roundToInt()}%"
                },
                onValueChange = { onChange(appearance.copy(glassStrength = if (it < 0.04f) 0f else it)) },
            )
        }

        SectionHeader(stringResource(R.string.appearance_glint))
        Card(pad) {
            Slider(
                title = stringResource(R.string.appearance_glint_strength),
                value = appearance.glintStrength,
                onValueChange = { onChange(appearance.copy(glintStrength = it)) },
            )
            ToggleRow(
                title = stringResource(R.string.appearance_tilt),
                subtitle = stringResource(R.string.appearance_tilt_hint),
                checked = appearance.tiltResponse,
                onCheckedChange = { onChange(appearance.copy(tiltResponse = it)) },
            )
        }

        SectionHeader(stringResource(R.string.appearance_text))
        Card(pad) {
            Slider(
                title = stringResource(R.string.appearance_text_size),
                value = appearance.textScale,
                range = Appearance.MIN_TEXT_SCALE..Appearance.MAX_TEXT_SCALE,
                valueLabel = "${(appearance.textScale * 100).roundToInt()}%",
                onValueChange = { onChange(appearance.copy(textScale = (it * 20).roundToInt() / 20f)) },
            )
            Text(
                text = stringResource(R.string.appearance_density),
                style = Blesk.type.bodyStrong,
                modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 8.dp),
            )
            Segmented(
                options = persistentListOf(
                    stringResource(R.string.appearance_density_compact),
                    stringResource(R.string.appearance_density_comfortable),
                ),
                selected = appearance.density.ordinal,
                onSelect = { onChange(appearance.copy(density = UiDensity.entries[it])) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        SectionHeader(stringResource(R.string.appearance_wallpaper))
        BackdropChoices(selected = files.background, hasPhoto = files.hasWallpaper, onSelect = files.onBackground)
        Card(pad) {
            ListRow(
                title = stringResource(R.string.appearance_wallpaper_pick),
                subtitle = stringResource(R.string.appearance_wallpaper_hint),
                icon = BleskIcons.Image,
                onClick = files.onPickWallpaper,
            )
            if (files.hasWallpaper) {
                ListRow(
                    title = stringResource(R.string.appearance_wallpaper_remove),
                    icon = BleskIcons.Close,
                    showChevron = false,
                    onClick = files.onRemoveWallpaper,
                )
            }
        }

        SectionHeader(stringResource(R.string.appearance_file))
        Card(pad) {
            ListRow(title = stringResource(R.string.appearance_save), icon = BleskIcons.Download, onClick = files.onSaveTheme)
            ListRow(title = stringResource(R.string.appearance_open), icon = BleskIcons.Upload, onClick = files.onOpenTheme)
        }
    }
}

private fun hueTrack(lightness: Float, chroma: Float): ImmutableList<Color> =
    (0..12).map { Oklch(lightness.coerceIn(0.3f, 0.9f), chroma, it * 30f).toColor() }.toImmutableList()

@Composable
fun ThemePicker(current: ThemeSpec, onSelect: (ThemeSpec) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Blesk.dimens.gutter)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val options = if (current.preset == ThemePreset.Custom) ThemeSpec.presets + current else ThemeSpec.presets
        options.forEach { spec ->
            ThemeSwatch(
                spec = spec,
                selected = current.preset == spec.preset,
                onClick = { onSelect(spec) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A miniature of the theme: its background, a protected capsule catching the light, a matte one. */
@Composable
private fun ThemeSwatch(spec: ThemeSpec, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = remember(spec) { BleskPalette.from(spec) }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.62f)
                .flashOn(selected, shape)
                .clip(shape)
                .background(colors.background)
                .border(
                    width = if (selected) 2.dp else Blesk.dimens.hairline,
                    color = if (selected) Blesk.colors.accent else Blesk.colors.hairline,
                    shape = shape,
                )
                .padding(8.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val capsule = RoundedCornerShape(8.dp)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(3.2f)
                        .clip(capsule)
                        .background(colors.surface)
                        .glint(capsule, color = colors.light, onDark = colors.isDark),
                )
                Box(Modifier.fillMaxWidth().aspectRatio(3.2f).clip(capsule).background(colors.surface))
                Box(Modifier.fillMaxWidth(0.6f).aspectRatio(3.2f).clip(capsule).background(colors.surfaceStrong))
                Box(Modifier.size(8.dp).background(colors.accent, CircleShape))
            }
        }
        Text(
            text = stringResource(themeName(spec.preset)),
            style = Blesk.type.label,
            color = if (selected) Blesk.colors.textPrimary else Blesk.colors.textSecondary,
            maxLines = 1,
        )
    }
}

internal fun themeName(preset: ThemePreset): Int = when (preset) {
    ThemePreset.Light -> R.string.appearance_theme_light
    ThemePreset.Dark -> R.string.appearance_theme_dark
    ThemePreset.Lilac -> R.string.appearance_theme_lilac
    ThemePreset.Lime -> R.string.appearance_theme_lime
    ThemePreset.Custom -> R.string.appearance_theme_custom
}

/** Small previews of every background a chat can lie on; the chosen one is outlined. */
@Composable
private fun BackdropChoices(selected: HeaderStyle?, hasPhoto: Boolean, onSelect: (HeaderStyle?) -> Unit) {
    val choices = buildList<Pair<HeaderStyle?, String>> {
        add(null to stringResource(R.string.appearance_background_none))
        repeat(HeaderStyle.Light.PRESETS) { add(HeaderStyle.Light(it) to stringResource(R.string.appearance_background_light, it + 1)) }
        repeat(HeaderStyle.Solid.TONES) { add(HeaderStyle.Solid(it) to stringResource(R.string.appearance_background_tone, it + 1)) }
        if (hasPhoto) add(HeaderStyle.Photo to stringResource(R.string.appearance_background_photo))
    }
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        choices.forEach { (style, label) ->
            val chosen = style == selected
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier
                    .size(56.dp, 84.dp)
                    .clip(shape)
                    .background(Blesk.colors.background)
                    .border(if (chosen) 2.dp else Blesk.dimens.hairline, if (chosen) Blesk.colors.accent else Blesk.colors.hairline, shape)
                    .selectable(selected = chosen, role = Role.RadioButton) {
                        haptics.tick()
                        onSelect(style)
                    }
                    .semantics { contentDescription = label },
            ) {
                ChatBackdrop(style, photo = null)
                if (style == HeaderStyle.Photo) {
                    Icon(BleskIcons.Image, contentDescription = null, modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
