package app.blesk.feature.chats.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList

/** The top of an info screen: the picture, the name and a line or two about who is there. */
@Composable
internal fun InfoHero(title: String, lines: List<String>, avatar: @Composable () -> Unit, tag: @Composable () -> Unit = {}) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter).padding(top = 8.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        avatar()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = title,
                style = Blesk.type.title,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.padding(top = 4.dp).semantics { heading() },
            )
            tag()
        }
        lines.filter { it.isNotBlank() }.forEach { line ->
            Text(line, style = Blesk.type.label, color = Blesk.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** Tab capsules that scroll sideways when they do not fit: «Участники · Медиа · Файлы · Ссылки · Голосовые». */
@Composable
internal fun InfoTabs(options: ImmutableList<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Blesk.dimens.gutter, vertical = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEachIndexed { index, label ->
            val active = index == selected
            Text(
                text = label,
                style = Blesk.type.bodyStrong,
                color = if (active) Blesk.colors.onAccent else Blesk.colors.textPrimary,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (active) Blesk.colors.accent else Blesk.colors.surfaceStrong)
                    .selectable(selected = active, role = Role.Tab) {
                        if (!active) {
                            haptics.tick()
                            onSelect(index)
                        }
                    }
                    .heightIn(min = Blesk.dimens.touchTarget)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
