package app.blesk.feature.chats

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.persistentListOf

/** The steps offered for disappearing messages, in seconds; 0 is off. */
val TimerSteps = listOf(0, 3_600, 86_400, 604_800)

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
