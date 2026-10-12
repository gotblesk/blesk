package app.blesk.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
internal fun DayDivider(date: LocalDate) {
    val today = LocalDate.now()
    val text = when (date) {
        today -> stringResource(R.string.chat_today)
        today.minusDays(1) -> stringResource(R.string.chat_yesterday)
        else -> date.format(if (date.year == today.year) dayFormat else dayYearFormat)
    }
    Text(
        text = text,
        style = Blesk.type.caption,
        color = Blesk.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics { heading() },
    )
}

private val dayFormat = DateTimeFormatter.ofPattern("d MMMM")
private val dayYearFormat = DateTimeFormatter.ofPattern("d MMMM yyyy")

@Composable
internal fun UnreadDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Blesk.colors.hairline))
        Text(stringResource(R.string.chat_unread_divider), style = Blesk.type.caption, color = Blesk.colors.textSecondary)
        Box(Modifier.weight(1f).height(1.dp).background(Blesk.colors.hairline))
    }
}

/** Round matte buttons over the messages: to the first unread mention of you, and to the bottom. */
@Composable
internal fun JumpButtons(
    showDown: Boolean,
    unseen: Int,
    mentions: Int,
    onDown: () -> Unit,
    onMention: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.End) {
        if (mentions > 0) {
            JumpButton(BleskIcons.At, pluralStringResource(R.plurals.chat_jump_mentions, mentions, mentions), mentions, onMention)
        }
        if (showDown) {
            val label = if (unseen > 0) {
                pluralStringResource(R.plurals.chat_jump_new, unseen, unseen)
            } else {
                stringResource(R.string.chat_jump_down)
            }
            JumpButton(BleskIcons.ArrowDown, label, unseen, onDown)
        }
    }
}

@Composable
private fun JumpButton(icon: ImageVector, label: String, count: Int, onClick: () -> Unit) {
    Box {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Blesk.colors.surfaceStrong)
                .border(1.dp, Blesk.colors.hairline, CircleShape)
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Blesk.colors.textPrimary)
        }
        if (count > 0) {
            Box(Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp).clearAndSetSemantics { }) {
                UnreadBadge(count)
            }
        }
    }
}
