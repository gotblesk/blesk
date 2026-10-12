package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** What can be done to a chat from the list. */
class ChatMenuActions(
    val onPin: (Boolean) -> Unit,
    val onArchive: (Boolean) -> Unit,
    /** Epoch millis; [Long.MAX_VALUE] for good, 0 to turn sound back on. */
    val onMute: (Long) -> Unit,
    val onMarkUnread: (Boolean) -> Unit,
)

/** A matte sheet over the list, opened by a long press on a chat. */
@Composable
fun ChatMenu(conversation: Conversation, now: Long, actions: ChatMenuActions, onDismiss: () -> Unit) {
    var muting by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .dragToDismiss(onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Text(
                text = conversation.title,
                style = Blesk.type.headline,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            Card {
                if (muting) {
                    MuteChoices(now) { until -> act { actions.onMute(until) } }
                    return@Card
                }
                if (conversation.kind != ConversationKind.Saved) {
                    ListRow(
                        title = stringResource(if (conversation.pinned) R.string.chats_unpin else R.string.chats_pin),
                        icon = BleskIcons.Pin,
                        showChevron = false,
                        onClick = {
                            if (!conversation.pinned) haptics.stick()
                            act { actions.onPin(!conversation.pinned) }
                        },
                    )
                    ListRow(
                        title = stringResource(if (conversation.archived) R.string.chats_from_archive else R.string.chats_to_archive),
                        icon = BleskIcons.Archive,
                        showChevron = false,
                        onClick = { act { actions.onArchive(!conversation.archived) } },
                    )
                    if (conversation.muted(now)) {
                        ListRow(
                            title = stringResource(R.string.chats_unmute),
                            icon = BleskIcons.Bell,
                            showChevron = false,
                            onClick = { act { actions.onMute(0) } },
                        )
                    } else {
                        ListRow(title = stringResource(R.string.chats_mute), icon = BleskIcons.BellOff, onClick = { muting = true })
                    }
                }
                val unread = conversation.unread > 0 || conversation.markedUnread
                ListRow(
                    title = stringResource(if (unread) R.string.chats_mark_read else R.string.chats_mark_unread),
                    icon = BleskIcons.Check,
                    showChevron = false,
                    onClick = { act { actions.onMarkUnread(!unread) } },
                )
            }
        }
    }
}

@Composable
private fun MuteChoices(now: Long, onChoose: (Long) -> Unit) {
    listOf(
        R.string.chats_mute_hour to now + HOUR,
        R.string.chats_mute_eight to now + 8 * HOUR,
        R.string.chats_mute_two_days to now + 48 * HOUR,
        R.string.chats_mute_forever to Long.MAX_VALUE,
    ).forEach { (label, until) ->
        ListRow(title = stringResource(label), icon = BleskIcons.BellOff, showChevron = false, onClick = { onChoose(until) })
    }
}

private const val SCRIM = 0.35f
private const val HOUR = 3_600_000L
