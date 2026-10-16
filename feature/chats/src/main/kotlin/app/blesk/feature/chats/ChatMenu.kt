package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.component.dismissOnTap
import app.blesk.core.design.component.dragToDismiss
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** What can be done to a chat from the list. */
class ChatMenuActions(
    val onPin: (Boolean) -> Unit,
    val onArchive: (Boolean) -> Unit,
    /** Epoch millis; [Long.MAX_VALUE] for good, 0 to turn sound back on. */
    val onMute: (Long) -> Unit,
    val onMarkUnread: (Boolean) -> Unit,
    /** «Удалить»: «у меня», or with forBoth «у меня и у собеседника»; null where a chat is not deleted this way. */
    val onDelete: ((forBoth: Boolean) -> Unit)? = null,
)

/**
 * Where each chat row is on screen, noted as the rows are placed, so the menu can lift the
 * very row that was pressed. Not state: it is only read at the moment of the long press.
 */
class ChatRowPlaces {
    private val places = HashMap<String, Rect>()

    fun put(id: String, bounds: Rect) {
        places[id] = bounds
    }

    operator fun get(id: String): Rect? = places[id]
}

val LocalChatRowPlaces = staticCompositionLocalOf<ChatRowPlaces?> { null }

/**
 * Opened by a long press on a chat. With the row's place known, the row flows out into a mini-chat
 * with the actions under it ([MiniChat]); without it, as for a room, a matte sheet at the bottom.
 */
@Composable
fun ChatMenu(
    conversation: Conversation,
    now: Long,
    actions: ChatMenuActions,
    onDismiss: () -> Unit,
    anchor: Rect? = null,
    messages: ImmutableList<Message> = persistentListOf(),
    authorName: (String) -> String? = { null },
    onOpen: (() -> Unit)? = null,
) {
    var muting by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    fun act(action: () -> Unit) {
        action()
        onDismiss()
    }
    val unread = conversation.unread > 0 || conversation.markedUnread
    val muted = conversation.muted(now)
    if (anchor != null) {
        val pinLabel = stringResource(if (conversation.pinned) R.string.chats_unpin_short else R.string.chats_pin_short)
        val archiveLabel =
            stringResource(if (conversation.archived) R.string.chats_from_archive_short else R.string.chats_to_archive)
        val muteLabel = stringResource(if (muted) R.string.chats_unmute_short else R.string.chats_mute)
        val readLabel = stringResource(if (unread) R.string.chats_read_short else R.string.chats_unread_short)
        val deleteLabel = stringResource(R.string.chats_delete_short)
        val cluster = buildList {
            add(ClusterAction(BleskIcons.Pin, pinLabel) {
                if (!conversation.pinned) haptics.stick()
                act { actions.onPin(!conversation.pinned) }
            })
            add(ClusterAction(BleskIcons.Archive, archiveLabel) { act { actions.onArchive(!conversation.archived) } })
            // Notes to yourself never ring, so there is nothing to silence.
            if (conversation.kind != ConversationKind.Saved) {
                add(ClusterAction(if (muted) BleskIcons.Bell else BleskIcons.BellOff, muteLabel) {
                    if (muted) act { actions.onMute(0) } else muting = true
                })
            }
            add(ClusterAction(BleskIcons.Check, readLabel) { act { actions.onMarkUnread(!unread) } })
            if (actions.onDelete != null) add(ClusterAction(BleskIcons.Trash, deleteLabel) { deleting = true })
        }
        MiniChat(conversation, anchor, messages, authorName, cluster, onOpen, onDismiss)
        if (muting) {
            ChoiceSheet(
                title = stringResource(R.string.chats_mute),
                choices = muteChoices(now).map { (label, until) -> Choice(stringResource(label)) { act { actions.onMute(until) } } },
                onDismiss = { muting = false },
            )
        }
        actions.onDelete?.takeIf { deleting }?.let { delete ->
            ChoiceSheet(
                title = stringResource(R.string.person_delete_chat_title),
                choices = buildList {
                    add(Choice(stringResource(R.string.chat_delete_for_me), danger = true) { act { delete(false) } })
                    if (conversation.kind == ConversationKind.Direct) {
                        val both = stringResource(R.string.person_delete_chat_both, conversation.title)
                        add(Choice(both, danger = true) { act { delete(true) } })
                    }
                },
                onDismiss = { deleting = false },
                text = if (conversation.kind == ConversationKind.Direct) stringResource(R.string.chat_delete_honest) else null,
            )
        }
        return
    }
    MenuSheet(title = conversation.title, onDismiss = onDismiss) {
        Card {
            if (muting) {
                MuteChoices(now) { until -> act { actions.onMute(until) } }
                return@Card
            }
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
            if (conversation.kind != ConversationKind.Saved) {
                if (muted) {
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
            ListRow(
                title = stringResource(if (unread) R.string.chats_mark_read else R.string.chats_mark_unread),
                icon = BleskIcons.Check,
                showChevron = false,
                onClick = { act { actions.onMarkUnread(!unread) } },
            )
        }
    }
}

/** A matte sheet at the bottom over a dimmed screen, titled with what it acts on; a tap outside or a swipe down closes it. */
@Composable
internal fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM))
            .dismissOnTap(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .dragToDismiss(onDismiss)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .blockTaps(),
        ) {
            Text(
                text = title,
                style = Blesk.type.headline,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            )
            content()
        }
    }
}

@Composable
private fun MuteChoices(now: Long, onChoose: (Long) -> Unit) {
    muteChoices(now).forEach { (label, until) ->
        ListRow(title = stringResource(label), icon = BleskIcons.BellOff, showChevron = false, onClick = { onChoose(until) })
    }
}

private fun muteChoices(now: Long) = listOf(
    R.string.chats_mute_hour to now + HOUR,
    R.string.chats_mute_eight to now + 8 * HOUR,
    R.string.chats_mute_two_days to now + 48 * HOUR,
    R.string.chats_mute_forever to Long.MAX_VALUE,
)

private const val SCRIM = 0.35f
private const val HOUR = 3_600_000L
