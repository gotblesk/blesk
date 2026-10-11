package app.blesk.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.glint
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskPalette
import app.blesk.shared.appearance.ThemePreset
import app.blesk.shared.appearance.ThemeSpec

@Composable
fun AppearanceScreen(
    current: ThemeSpec,
    onSelectTheme: (ThemeSpec) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ScrollScreen(title = stringResource(R.string.settings_appearance), onBack = onBack, modifier = modifier) {
        SectionHeader(stringResource(R.string.appearance_theme))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Blesk.dimens.gutter)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeSpec.presets.forEach { spec ->
                ThemeSwatch(
                    spec = spec,
                    selected = current.preset == spec.preset,
                    onClick = { onSelectTheme(spec) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = Blesk.dimens.sectionGap)) {
            ListRow(
                title = stringResource(R.string.appearance_more),
                subtitle = stringResource(R.string.appearance_more_hint),
                soon = true,
            )
        }
    }
}

/** A miniature of the theme: its background, a matte capsule and a protected one catching the light. */
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
