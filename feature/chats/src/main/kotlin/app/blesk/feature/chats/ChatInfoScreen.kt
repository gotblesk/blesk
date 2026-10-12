package app.blesk.feature.chats

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf

/** The steps offered for disappearing messages, in seconds; 0 is off. */
val TimerSteps = listOf(0, 3_600, 86_400, 604_800)

/** About a one-to-one chat: who it is, checking keys, disappearing messages, pinning. */
@Composable
fun ChatInfoScreen(
    conversation: Conversation,
    username: String,
    verified: Boolean,
    /** Null in the demo, where there are no keys to check. */
    onVerify: (() -> Unit)?,
    onTimer: (Int) -> Unit,
    onPin: (Boolean) -> Unit,
    onBack: () -> Unit,
    onTheme: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ScrollScreen(title = conversation.title, onBack = onBack, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(conversation.title, size = 96.dp)
            Text(conversation.title, style = Blesk.type.headline, modifier = Modifier.padding(top = 12.dp))
            Text("@$username", style = Blesk.type.label, color = Blesk.colors.textSecondary)
        }
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            if (onVerify != null) {
                ListRow(
                    title = stringResource(R.string.chat_verify),
                    subtitle = stringResource(if (verified) R.string.info_key_verified else R.string.info_key_not_verified),
                    icon = BleskIcons.Shield,
                    onClick = onVerify,
                )
            }
            ToggleRow(
                title = stringResource(R.string.chats_pin),
                icon = BleskIcons.Pin,
                checked = conversation.pinned,
                onCheckedChange = onPin,
            )
            onTheme?.let { ListRow(title = stringResource(R.string.chat_theme), icon = BleskIcons.Appearance, onClick = it) }
        }
        TimerSection(conversation.timerSeconds, onTimer)
    }
}

/** Disappearing messages: the same for everyone in the chat. */
@Composable
fun TimerSection(seconds: Int, onTimer: (Int) -> Unit) {
    SectionHeader(stringResource(R.string.info_timer))
    Segmented(
        options = persistentListOf(
            stringResource(R.string.info_timer_off),
            stringResource(R.string.info_timer_hour),
            stringResource(R.string.info_timer_day),
            stringResource(R.string.info_timer_week),
        ),
        selected = TimerSteps.indexOf(seconds).coerceAtLeast(0),
        onSelect = { onTimer(TimerSteps[it]) },
    )
    Text(
        text = stringResource(R.string.info_timer_hint),
        style = Blesk.type.label,
        color = Blesk.colors.textSecondary,
        modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = Blesk.dimens.rowGap),
    )
}

/** "1 день" and the like, for the chat header. */
@Composable
fun timerLabel(seconds: Int): String = when (seconds) {
    TimerSteps[1] -> stringResource(R.string.info_timer_hour)
    TimerSteps[2] -> stringResource(R.string.info_timer_day)
    TimerSteps[3] -> stringResource(R.string.info_timer_week)
    else -> stringResource(R.string.info_timer_custom)
}
