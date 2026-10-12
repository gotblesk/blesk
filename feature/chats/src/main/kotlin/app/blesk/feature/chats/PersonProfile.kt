package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.Protection
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ProfileHero
import app.blesk.core.design.component.Segmented
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.component.glint
import app.blesk.core.design.component.profileHeaderHeight
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.condense
import coil3.compose.AsyncImage
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import app.blesk.core.design.R as DesignR

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
    val onDeleteChat: (() -> Unit)?,
    val onOpenMedia: (messageId: String) -> Unit,
    val onOpenFile: (Attachment.File) -> Unit,
    val onOpenLink: (String) -> Unit,
    val onPlayVoice: (path: String) -> Unit,
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
) {
    val scroll = rememberScrollState()
    var more by remember { mutableStateOf(false) }
    val headerPx = with(LocalDensity.current) { profileHeaderHeight().toPx() }
    val preset = conversation.id.hashCode().mod(HeaderStyle.Light.PRESETS)
    Box(modifier.fillMaxSize().background(Blesk.colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 24.dp),
        ) {
            ProfileHero(
                name = conversation.title,
                key = conversation.id,
                photo = null,
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
                    .semantics { heading() },
            )
            Text(
                text = "@$username",
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
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
            EncryptionRow(conversation.protection == Protection.EndToEnd)
            actions.onVerify?.let { verify -> KeysRow(verified, verify) }
            SharedContent(messages, actions)
            TimerSection(conversation.timerSeconds, actions.onTimer)
            actions.onDeleteChat?.let { delete ->
                Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = Blesk.dimens.sectionGap)) {
                    ListRow(
                        title = stringResource(R.string.person_delete_chat),
                        icon = BleskIcons.Trash,
                        showChevron = false,
                        onClick = delete,
                    )
                }
            }
        }
        // The lens gathers above the page once the avatar has gone up into it.
        if (scroll.value > headerPx - LENS_REACH_PX * LocalDensity.current.density) {
            TopLens(title = conversation.title, onBack = onBack)
        } else {
            Box(Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(10.dp)) {
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Blesk.colors.background.copy(alpha = 0.6f))) {
                    IconButton(BleskIcons.Back, stringResource(DesignR.string.design_back), onBack)
                }
            }
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

private enum class SharedTab { Media, Files, Links, Voice }

/** What the two of you have sent each other, one kind at a time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SharedContent(messages: ImmutableList<Message>, actions: PersonActions) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Segmented(
        options = persistentListOf(
            stringResource(R.string.person_tab_media),
            stringResource(R.string.person_tab_files),
            stringResource(R.string.person_tab_links),
            stringResource(R.string.person_tab_voice),
        ),
        selected = tab,
        onSelect = { tab = it },
    )
    val newest = messages.asReversed()
    val gutter = Modifier.padding(horizontal = Blesk.dimens.gutter)
    when (SharedTab.entries[tab]) {
        SharedTab.Media -> {
            val media = newest.filter { it.attachment is Attachment.Photo || it.attachment is Attachment.Video }
            if (media.isEmpty()) return Empty()
            FlowRow(
                gutter.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                media.take(MAX_SHOWN).forEach { message ->
                    val path = when (val a = message.attachment) {
                        is Attachment.Photo -> a.path
                        is Attachment.Video -> a.thumbnail
                        else -> return@forEach
                    }
                    val label = stringResource(R.string.person_open_media)
                    AsyncImage(
                        model = File(path),
                        contentDescription = label,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth(THIRD)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Blesk.colors.surface)
                            .clickable(role = Role.Button) { actions.onOpenMedia(message.id) },
                    )
                }
            }
        }
        SharedTab.Files -> {
            val files = newest.mapNotNull { it.attachment as? Attachment.File }
            if (files.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                files.take(MAX_SHOWN).forEach { file ->
                    ListRow(
                        title = file.name,
                        subtitle = sizeLabel(file.sizeBytes),
                        icon = BleskIcons.Document,
                        showChevron = false,
                        onClick = { actions.onOpenFile(file) },
                    )
                }
            }
        }
        SharedTab.Links -> {
            val links = newest.flatMap { message -> LINK.findAll(message.text).map { it.value }.toList() }.distinct()
            if (links.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                links.take(MAX_SHOWN).forEach { link ->
                    ListRow(title = link, icon = BleskIcons.Forward, showChevron = false, onClick = { actions.onOpenLink(link) })
                }
            }
        }
        SharedTab.Voice -> {
            val voices = newest.filter { it.attachment is Attachment.Voice }
            if (voices.isEmpty()) return Empty()
            Card(gutter.padding(top = 8.dp)) {
                voices.take(MAX_SHOWN).forEach { message ->
                    val voice = message.attachment as Attachment.Voice
                    ListRow(
                        title = dateFormat.format(message.sentAt.atZone(ZoneId.systemDefault())),
                        subtitle = durationLabel(voice.durationMillis),
                        icon = BleskIcons.Play,
                        showChevron = false,
                        onClick = { actions.onPlayVoice(voice.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Empty() {
    StateMessage(stringResource(R.string.person_nothing_yet))
}

@Composable
private fun MoreSheet(pinned: Boolean, onPin: () -> Unit, onTheme: (() -> Unit)?, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
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

@Composable
private fun sizeLabel(bytes: Long): String = when {
    bytes >= MEGABYTE -> stringResource(R.string.person_size_mb, bytes / MEGABYTE.toFloat())
    else -> stringResource(R.string.person_size_kb, (bytes / KILOBYTE).coerceAtLeast(1))
}

private fun durationLabel(millis: Long): String {
    val seconds = millis / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

private val LINK = Regex("https?://[^\\s]+")
private val dateFormat = DateTimeFormatter.ofPattern("d MMMM, HH:mm", Locale.forLanguageTag("ru"))
private const val MAX_SHOWN = 60
private const val THIRD = 0.32f
private const val MEGABYTE = 1_048_576L
private const val KILOBYTE = 1_024L
private const val LENS_REACH_PX = 72f
