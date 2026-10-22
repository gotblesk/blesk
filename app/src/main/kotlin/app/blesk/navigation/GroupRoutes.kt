package app.blesk.navigation

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.calls.CallActivity
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.SpaceResult
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.settings.NotificationSettings
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.theme.LocalClock
import app.blesk.feature.chats.ArchiveActions
import app.blesk.feature.chats.groups.ChannelInfoActions
import app.blesk.feature.chats.groups.ChannelInfoScreen
import app.blesk.feature.chats.groups.ChannelMember
import app.blesk.feature.chats.groups.ChannelNotify
import app.blesk.feature.chats.groups.GroupActions
import app.blesk.feature.chats.groups.GroupInfoExtras
import app.blesk.feature.chats.groups.GroupInfoScreen
import app.blesk.feature.chats.groups.NewGroupScreen
import app.blesk.feature.chats.groups.NewKind
import app.blesk.feature.chats.groups.SpaceActions
import app.blesk.feature.chats.groups.SpaceInfoScreen
import app.blesk.shared.protocol.GroupChannelKind
import app.blesk.shared.protocol.GroupKind
import app.blesk.shared.protocol.InviteLink
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** [onCreated] gets the new conversation and whether it is a closed room, whose details open first. */
@Composable
internal fun NewGroupRoute(container: AppContainer, kind: NewKind, onCreated: (String, Boolean) -> Unit, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val spaces = chats as? SpaceController
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    NewGroupScreen(
        contacts = contacts.toImmutableList(),
        onCreate = { spec ->
            when (spec.kind) {
                NewKind.Group, NewKind.Room -> {
                    val kind = if (spec.kind == NewKind.Room) GroupKind.Room else GroupKind.Group
                    onCreated(groups.createGroup(kind, spec.title, spec.members), kind == GroupKind.Room)
                    SpaceResult.Ok
                }
                NewKind.Channel, NewKind.OpenRoom -> {
                    val kind = if (spec.kind == NewKind.Channel) SpaceKind.Channel else SpaceKind.Room
                    val (result, id) = spaces?.createSpace(kind, spec.handle, spec.title, spec.about) ?: (SpaceResult.Failed to null)
                    id?.let { onCreated(it, false) }
                    result
                }
            }
        },
        onBack = onBack,
        initialKind = kind,
    )
}

@Composable
internal fun SpaceInfoRoute(
    container: AppContainer,
    handle: String,
    onLeft: () -> Unit,
    onBack: () -> Unit,
    onOpenMedia: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val spaces = chats as? SpaceController ?: return
    val me = (chats as? GroupController)?.me.orEmpty()
    val space by remember(handle) { spaces.space(SPACE_PREFIX + handle) }.collectAsStateWithLifecycle(initialValue = null)
    var version by remember { mutableIntStateOf(0) }
    var membersFailed by remember { mutableStateOf(false) }
    val members by produceState<List<SpaceMember>?>(null, handle, version, space?.role) {
        value = null
        membersFailed = false
        val role = space?.role
        val loaded = if (role == SpaceRole.Owner || role == SpaceRole.Admin) spaces.spaceMembers(handle) else emptyList()
        membersFailed = loaded == null
        value = loaded
    }
    val context = LocalContext.current
    val ids = remember(space) {
        listOf(SPACE_PREFIX + handle) + space?.channels.orEmpty().map { "$SPACE_PREFIX$handle:${it.id}" }
    }
    val messages by remember(ids) {
        combine(ids.map { chats.messages(it) }) { lists -> lists.toList().flatten().sortedBy { it.sentAt } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val current = space ?: return
    var themeSheet by remember { mutableStateOf(false) }
    SpaceInfoScreen(
        onTheme = { themeSheet = true },
        onSaveCard = { image -> saveCardToGallery(context, image) },
        messages = messages.toImmutableList(),
        archive = ArchiveActions(
            onOpenMedia = { id -> messages.firstOrNull { it.id == id }?.let { onOpenMedia(it.conversationId, id) } },
            onOpenFile = { file -> openFile(context, file) },
            onOpenLink = { link -> openMessageLink(container, context, link) },
            onPlayVoice = { path -> container.audioPlayer.toggle(path) },
        ),
        space = current,
        me = me,
        members = members?.toImmutableList(),
        membersFailed = membersFailed,
        onRetryMembers = { version++ },
        nameOf = { username -> chats.contact(username)?.name ?: username },
        actions = SpaceActions(
            update = { title, about -> spaces.updateSpace(handle, title, about) },
            setRole = { username, role ->
                spaces.setSpaceRole(handle, username, role)
                version++
            },
            ban = { username ->
                spaces.ban(handle, username)
                version++
            },
            addChannel = { spaces.addSpaceChannel(handle, it) },
            removeChannel = { spaces.removeSpaceChannel(handle, it) },
            leave = {
                spaces.leaveSpace(handle)
                onLeft()
            },
            delete = {
                if (spaces.deleteSpace(handle)) onLeft()
            },
        ),
        onBack = onBack,
    )
    if (themeSheet) ConversationThemeSheet(container, ids, sendTo = null, onDismiss = { themeSheet = false })
}

private const val SPACE_PREFIX = "space:"

@Composable
internal fun GroupInfoRoute(
    container: AppContainer,
    groupId: String,
    onLeft: () -> Unit,
    editing: Boolean = false,
    onBack: () -> Unit,
    onOpenPerson: (username: String) -> Unit = {},
    onOpenMedia: (conversationId: String, messageId: String) -> Unit = { _, _ -> },
    /** Set in the demo, where a voice channel is a picture to look at rather than a call. */
    onOpenVoice: ((channelId: String) -> Unit)? = null,
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val state by remember(groupId) { groups.group(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
    val conversation by remember(groupId) { chats.conversation(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val leftText = stringResource(app.blesk.feature.chats.R.string.group_left)
    val context = LocalContext.current
    val notify = rememberNotify()
    val callsLater = stringResource(app.blesk.feature.chats.R.string.group_calls_later)
    // A room keeps its messages in its text channels; the archive gathers them all.
    val conversationIds = remember(state) {
        listOf(groupId) + state?.channels.orEmpty().filter { it.kind == GroupChannelKind.Text }.map { "$groupId:${it.id}" }
    }
    val messages by remember(conversationIds) {
        combine(conversationIds.map { chats.messages(it) }) { lists -> lists.toList().flatten().sortedBy { it.sentAt } }
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val presence by chats.presence.collectAsStateWithLifecycle()
    val clock = LocalClock.current
    val online = remember(presence) {
        val now = clock()
        presence.filterValues { it.isOnline(now) }.keys.map { chats.contact(it)?.username ?: it }.toImmutableSet()
    }
    var themeSheet by remember { mutableStateOf(false) }
    val gate = rememberIdentityGate(container)
    val current = state ?: return
    val extras = GroupInfoExtras(
        inviteLink = if (chats is DemoRepository) null else ({ code -> InviteLink.format(groupId, groups.me, code) }),
        gate = gate,
        onGone = onLeft,
        startEditing = editing,
        pinned = conversation?.pinned == true,
        archived = conversation?.archived == true,
        onPin = { pin -> chats.setChatPinned(groupId, pin) },
        onArchive = { archive -> chats.setArchived(groupId, archive) },
        onTheme = { themeSheet = true },
        messages = messages.toImmutableList(),
        online = online,
        muted = conversation?.muted(clock().toEpochMilli()) == true,
        onMute = { mute -> chats.mute(groupId, if (mute) Long.MAX_VALUE else 0L) },
        onCall = {
            // A room talks in its voice channels; a group has no call of its own yet.
            val voice = current.channels.firstOrNull { it.kind == GroupChannelKind.Voice }
            when {
                voice == null -> notify(callsLater)
                onOpenVoice != null -> onOpenVoice(voice.id)
                else -> context.startActivity(CallActivity.voice(context, groupId, voice.id, voice.id, "${current.title} · ${voice.title}"))
            }
        },
        onOpenPerson = onOpenPerson,
        archive = ArchiveActions(
            onOpenMedia = { id -> messages.firstOrNull { it.id == id }?.let { onOpenMedia(it.conversationId, id) } },
            onOpenFile = { file -> openFile(context, file) },
            onOpenLink = { link -> openMessageLink(container, context, link) },
            onPlayVoice = { path -> container.audioPlayer.toggle(path) },
        ),
    )
    GroupInfoScreen(
        extras = extras,
        timerSeconds = conversation?.timerSeconds ?: 0,
        onTimer = { chats.setTimer(groupId, it) },
        state = current,
        me = groups.me,
        contacts = contacts.toImmutableList(),
        nameOf = { username -> chats.contact(username)?.name ?: username },
        actions = GroupActions(
            addMembers = { groups.addMembers(groupId, it) },
            addChannel = { title, kind, adminsOnly -> groups.addChannel(groupId, title, kind, adminsOnly) },
            removeChannel = { groups.removeChannel(groupId, it) },
            change = { groups.change(groupId, it) },
            // The group leaves the list at once; the leaving itself waits out «Отменить».
            leave = {
                container.notices.undoable(leftText, hidden = listOf(groupId)) {
                    container.scope.launch { groups.leave(groupId) }
                }
                onLeft()
            },
        ),
        onBack = onBack,
    )
    if (themeSheet) {
        // A room's messages live in its channels, so a shared theme goes into the first text one, or nowhere.
        val sendTo = conversation?.let { chat -> chat.chatTarget().takeUnless { it == chat.id && chat.channels.isNotEmpty() } }
        ConversationThemeSheet(container, conversationIds, sendTo = sendTo, onDismiss = { themeSheet = false })
    }
}

/** A room's channel, opened from its header; [id] is the channel's conversation, `room:channel`. */
@Composable
internal fun ChannelInfoRoute(
    container: AppContainer,
    id: String,
    onOpenRoom: (groupId: String) -> Unit,
    onOpenMessage: (messageId: String) -> Unit,
    onOpenPerson: (username: String) -> Unit,
    onOpenMedia: (conversationId: String, messageId: String) -> Unit,
    onBack: () -> Unit,
) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val groups = chats as? GroupController ?: return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupId = id.substringBefore(':')
    val state by remember(groupId) { groups.group(groupId) }.collectAsStateWithLifecycle(initialValue = null)
    val conversation by remember(id) { chats.conversation(id) }.collectAsStateWithLifecycle(initialValue = null)
    val messages by remember(id) { chats.messages(id) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val notifications by container.settings.notifications.collectAsStateWithLifecycle(initialValue = NotificationSettings())
    val ownName by remember { container.settings.profile.map { it.name } }.collectAsStateWithLifecycle(initialValue = "")
    var themeSheet by remember { mutableStateOf(false) }
    val room = state ?: return
    val channel = room.channels.firstOrNull { it.id == id.substringAfter(':', "") } ?: return
    val notify = when {
        conversation?.muted(LocalClock.current().toEpochMilli()) == true -> ChannelNotify.Nothing
        id in notifications.mentionsOnly -> ChannelNotify.Mentions
        else -> ChannelNotify.All
    }
    val pinnedIds = conversation?.pinnedMessageIds.orEmpty()
    ChannelInfoScreen(
        title = channel.title,
        room = room.title,
        notify = notify,
        messages = messages.toImmutableList(),
        pinned = messages.filter { it.id in pinnedIds }.toImmutableList(),
        members = room.members.map { member ->
            val you = member.username == groups.me
            val name = if (you) ownName.ifBlank { member.username } else chats.contact(member.username)?.name ?: member.username
            ChannelMember(member.username, name, you)
        }.toImmutableList(),
        actions = ChannelInfoActions(
            onNotify = { choice ->
                chats.mute(id, if (choice == ChannelNotify.Nothing) Long.MAX_VALUE else 0L)
                scope.launch {
                    container.settings.updateNotifications { settings ->
                        val rest = settings.mentionsOnly - id
                        settings.copy(mentionsOnly = if (choice == ChannelNotify.Mentions) rest + id else rest)
                    }
                }
            },
            onOpenRoom = { onOpenRoom(groupId) },
            onOpenMessage = onOpenMessage,
            onOpenPerson = onOpenPerson,
            onTheme = { themeSheet = true },
            archive = ArchiveActions(
                onOpenMedia = { messageId -> onOpenMedia(id, messageId) },
                onOpenFile = { file -> openFile(context, file) },
                onOpenLink = { link -> openMessageLink(container, context, link) },
                onPlayVoice = { path -> container.audioPlayer.toggle(path) },
            ),
        ),
        onBack = onBack,
    )
    if (themeSheet) {
        ConversationThemeSheet(container, listOf(id), sendTo = id, onDismiss = { themeSheet = false })
    }
}

/** A milestone card into the gallery, under «Pictures/blesk», as a PNG. */
private suspend fun saveCardToGallery(context: Context, image: ImageBitmap): Boolean = withContext(Dispatchers.IO) {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "blesk_${System.currentTimeMillis()}.png")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/blesk")
    }
    runCatching {
        val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return@runCatching false
        context.contentResolver.openOutputStream(uri)?.use { output ->
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, output)
        } == true
    }.getOrDefault(false)
}

private const val PNG_QUALITY = 100
