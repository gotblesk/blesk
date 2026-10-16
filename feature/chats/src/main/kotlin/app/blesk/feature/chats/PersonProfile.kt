package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ProfileHero
import app.blesk.core.design.component.ProfileTopLens
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.dismissOnTap
import app.blesk.core.design.component.glint
import app.blesk.core.design.component.nameIntoLens
import app.blesk.core.design.component.profileHeaderHeight
import app.blesk.core.design.component.rememberPhotoFile
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.condense
import app.blesk.feature.chats.locks.ChatLockEntry
import app.blesk.feature.chats.locks.LocalChatLockControls
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import java.io.File

/** What can be done from someone's profile. Null actions are not offered here. */
class PersonActions(
    val onWrite: () -> Unit,
    val onCall: ((video: Boolean) -> Unit)?,
    val onMute: (Boolean) -> Unit,
    val onPin: (Boolean) -> Unit,
    val onTimer: (Int) -> Unit,
    /** Null in the demo, where there are no keys to check. */
    val onVerify: (() -> Unit)?,
    val onTheme: (() -> Unit)?,
    /** «у меня», or with forBoth «у меня и у собеседника». */
    val onDeleteChat: ((forBoth: Boolean) -> Unit)?,
    val onOpenMedia: (messageId: String) -> Unit,
    val onOpenFile: (Attachment.File) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onPlayVoice: (path: String) -> Unit,
    /** Blocks or unblocks; null where there is nobody to block, as with bots. */
    val onBlock: ((Boolean) -> Unit)? = null,
    val blocked: Boolean = false,
    val onOpenConversation: (String) -> Unit = {},
)

/**
 * Someone's profile, laid out like your own: the melting header with the avatar on its edge,
 * the action cluster, whether your keys are checked, then what you have sent each other —
 * media, files, links and voice messages — and the chat's own settings.
 */
@Composable
fun PersonProfileScreen(
    conversation: Conversation,
    username: String,
    verified: Boolean,
    muted: Boolean,
    messages: ImmutableList<Message>,
    actions: PersonActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Groups and rooms you are both in. */
    shared: ImmutableList<Conversation> = persistentListOf(),
    /** What they said about themselves, if they shared it. */
    about: Contact? = null,
) {
    val photo = rememberPhotoFile(conversation.photo)
    val scroll = rememberScrollState()
    var more by remember { mutableStateOf(false) }
    var deletingChat by remember { mutableStateOf(false) }
    var confirmingBlock by remember { mutableStateOf(false) }
    val headerPx = with(LocalDensity.current) { profileHeaderHeight().toPx() }
    val preset = conversation.id.hashCode().mod(HeaderStyle.Light.PRESETS)
    LensScaffold(
        modifier = modifier,
        top = { ProfileTopLens(conversation.title, { scroll.value }, onBack) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            ProfileHero(
                name = conversation.title,
                key = conversation.id,
                photo = photo,
                header = HeaderStyle.Light(preset),
                headerPhoto = null,
                scroll = { scroll.value },
            )
            Text(
                text = conversation.title,
                style = condense(Blesk.type.title, scroll.value / headerPx),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, start = Blesk.dimens.gutter, end = Blesk.dimens.gutter)
                    // The big name flows into the lens: it is gone by the time the lens holds the name,
                    // never both at once over the status bar.
                    .nameIntoLens { scroll.value }
                    .semantics { heading() },
            )
            Text(
                text = "@$username",
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            about?.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    text = bio,
                    color = Blesk.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter).padding(top = 8.dp),
                )
            }
            ActionCluster(
                buildList {
                    add(ClusterAction(BleskIcons.Chats, stringResource(R.string.person_write), actions.onWrite))
                    actions.onCall?.let { call ->
                        add(ClusterAction(BleskIcons.Phone, stringResource(R.string.person_call)) { call(false) })
                        add(ClusterAction(BleskIcons.Video, stringResource(R.string.person_video)) { call(true) })
                    }
                    add(
                        ClusterAction(
                            if (muted) BleskIcons.Bell else BleskIcons.BellOff,
                            stringResource(if (muted) R.string.person_unmute else R.string.person_mute),
                        ) { actions.onMute(!muted) },
                    )
                    add(ClusterAction(BleskIcons.More, stringResource(R.string.person_more)) { more = true })
                },
                Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 16.dp),
            )
            about?.links?.takeIf { it.isNotEmpty() }?.let { links ->
                Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = Blesk.dimens.sectionGap)) {
                    links.forEach { link ->
                        ListRow(title = link.removePrefix("https://"), icon = BleskIcons.Share, onClick = { actions.onOpenLink(link) })
                    }
                }
            }
            EncryptionRow(conversation.protection == Protection.EndToEnd)
            actions.onVerify?.let { verify -> KeysRow(verified, verify) }
            SharedContent(messages, actions)
            if (shared.isNotEmpty()) {
                SectionHeader(stringResource(R.string.person_shared_groups))
                Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
                    shared.forEach { group ->
                        ListRow(
                            title = group.title,
                            icon = if (group.kind == ConversationKind.Room) BleskIcons.Rooms else BleskIcons.People,
                            onClick = { actions.onOpenConversation(group.id) },
                        )
                    }
                }
            }
            TimerSection(conversation.timerSeconds, actions.onTimer)
            if (LocalChatLockControls.current != null) {
                Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = Blesk.dimens.sectionGap)) { ChatLockEntry() }
            }
            if (actions.onDeleteChat != null || actions.onBlock != null) {
                Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = Blesk.dimens.sectionGap)) {
                    actions.onBlock?.let { block ->
                        ListRow(
                            title = stringResource(if (actions.blocked) R.string.person_unblock else R.string.person_block),
                            subtitle = stringResource(if (actions.blocked) R.string.person_unblock_hint else R.string.person_block_hint),
                            icon = BleskIcons.Shield,
                            showChevron = false,
                            // Blocking asks first, unblocking does not: nothing is lost by it.
                            onClick = { if (actions.blocked) block(false) else confirmingBlock = true },
                        )
                    }
                    if (actions.onDeleteChat != null) {
                        ListRow(
                            title = stringResource(R.string.person_delete_chat),
                            icon = BleskIcons.Trash,
                            showChevron = false,
                            onClick = { deletingChat = true },
                        )
                    }
                }
            }
        }
        actions.onDeleteChat?.takeIf { deletingChat }?.let { delete ->
            ChoiceSheet(
                title = stringResource(R.string.person_delete_chat_title),
                choices = listOf(
                    Choice(stringResource(R.string.chat_delete_for_me), danger = true) { delete(false) },
                    Choice(stringResource(R.string.person_delete_chat_both, conversation.title), danger = true) { delete(true) },
                ),
                onDismiss = { deletingChat = false },
                text = stringResource(R.string.chat_delete_honest),
            )
        }
        actions.onBlock?.takeIf { confirmingBlock }?.let { block ->
            ChoiceSheet(
                title = stringResource(R.string.person_block_title, conversation.title),
                text = stringResource(R.string.person_block_explained, conversation.title),
                choices = listOf(Choice(stringResource(R.string.person_block), danger = true) { block(true) }),
                onDismiss = { confirmingBlock = false },
            )
        }
        if (more) {
            MoreSheet(
                pinned = conversation.pinned,
                onPin = { actions.onPin(!conversation.pinned) },
                onTheme = actions.onTheme,
                onDismiss = { more = false },
            )
        }
    }
}

/** «Ключи сверены» glints like everything protected; until then it offers the check. */
@Composable
private fun KeysRow(verified: Boolean, onVerify: () -> Unit) {
    Card(
        Modifier
            .padding(horizontal = Blesk.dimens.gutter)
            .padding(bottom = Blesk.dimens.sectionGap)
            .let { if (verified) it.glint(RoundedCornerShape(Blesk.dimens.cardRadius)) else it },
    ) {
        ListRow(
            title = stringResource(if (verified) R.string.person_keys_verified else R.string.person_keys_check),
            subtitle = stringResource(if (verified) R.string.person_keys_verified_hint else R.string.person_keys_check_hint),
            icon = if (verified) BleskIcons.Shield else BleskIcons.Qr,
            onClick = onVerify,
        )
    }
}

/** What the two of you have sent each other, one kind at a time. */
@Composable
private fun SharedContent(messages: ImmutableList<Message>, actions: PersonActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val kinds = ArchiveKind.entries
    Segmented(
        options = kinds.map { stringResource(it.label) }.toImmutableList(),
        selected = tab,
        onSelect = { tab = it },
    )
    Archive(
        kind = kinds[tab],
        messages = messages,
        actions = ArchiveActions(actions.onOpenMedia, actions.onOpenFile, actions.onOpenLink, actions.onPlayVoice),
    )
}

@Composable
private fun MoreSheet(pinned: Boolean, onPin: () -> Unit, onTheme: (() -> Unit)?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .dismissOnTap(onDismiss),
    ) {
        Card(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .fillMaxWidth(),
        ) {
            ToggleRow(title = stringResource(R.string.chats_pin), icon = BleskIcons.Pin, checked = pinned, onCheckedChange = { onPin() })
            onTheme?.let { theme ->
                ListRow(
                    title = stringResource(R.string.chat_theme),
                    icon = BleskIcons.Appearance,
                    onClick = {
                        onDismiss()
                        theme()
                    },
                )
            }
        }
    }
}
