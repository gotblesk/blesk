package app.blesk.navigation

import android.Manifest
import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.BleskApplication
import app.blesk.R
import app.blesk.core.data.BotController
import app.blesk.core.data.Connection
import app.blesk.core.data.GroupController
import app.blesk.core.data.KeyState
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.SpaceController
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.model.Attachment
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.GLINT_REACTION
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.GestureHint
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.component.ChatBackdrop
import app.blesk.core.design.component.rememberNotify
import app.blesk.core.design.theme.BleskTheme
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.chats.QualitySheet
import app.blesk.feature.chats.media.MediaController
import app.blesk.feature.chats.media.Recorders
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.protocol.BotCommand
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ConversationRoute(
    container: AppContainer,
    id: String,
    appearance: Appearance,
    focusMessageId: String? = null,
    onOpenMedia: (messageId: String, index: Int) -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onOpenSpace: (handle: String) -> Unit,
    onCall: (video: Boolean) -> Unit,
    onVerify: () -> Unit,
    onForward: (messageIds: List<String>) -> Unit,
    onBack: () -> Unit,
    onOpenPerson: (conversationId: String) -> Unit = {},
) {
    val demo = container.chats.collectAsStateWithLifecycle().value ?: return
    val scope = rememberCoroutineScope()
    val conversation by demo.conversation(id).collectAsStateWithLifecycle(initialValue = null)
    val loaded by demo.messages(id).collectAsStateWithLifecycle(initialValue = null)
    val typing by demo.typing.collectAsStateWithLifecycle()
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    val chatThemes by container.settings.chatThemes.collectAsStateWithLifecycle(initialValue = emptyMap())
    val wallpaperVersion by container.settings.wallpaperVersion.collectAsStateWithLifecycle(initialValue = 0L)
    val background by container.settings.chatBackground.collectAsStateWithLifecycle(initialValue = null)
    val hintsSeen by container.settings.hintsSeen
        .map { seen -> GestureHint.entries.filter { it.name in seen }.toSet() }
        .collectAsStateWithLifecycle(initialValue = GestureHint.entries.toSet())
    val presence by demo.presence.collectAsStateWithLifecycle()
    val requests = requestActions(container, demo, onDone = onBack)
    val blockedPeople by remember(demo) { demo.blocked }.collectAsStateWithLifecycle(initialValue = emptyList())
    val doubleTapReaction by container.settings.doubleTapReaction.collectAsStateWithLifecycle(initialValue = GLINT_REACTION)
    val wallpaper by produceState<ImageBitmap?>(null, wallpaperVersion) {
        value = container.wallpaper.load()?.asImageBitmap()
    }
    var themeSheet by remember { mutableStateOf(false) }
    val override = chatThemes[id]
    val caption = stringResource(R.string.chat_theme_caption)
    // Read before marking, so the chat can open on the unread divider.
    val unreadAtOpen by produceState<Int?>(null, id) {
        value = demo.conversation(id).first()?.unread ?: 0
        demo.markRead(id)
    }
    val connection by remember(demo) { demo.connection }.collectAsStateWithLifecycle(initialValue = Connection.Online)
    val sendByEnter by container.settings.sendByEnter.collectAsStateWithLifecycle(initialValue = false)
    val recentEmoji by container.settings.recentEmoji.collectAsStateWithLifecycle(initialValue = emptyList())
    val application = LocalContext.current.applicationContext as? BleskApplication
    DisposableEffect(id) {
        container.openConversation = id
        application?.notifier?.clear(id)
        onDispose { if (container.openConversation == id) container.openConversation = null }
    }
    val groupController = demo as? GroupController
    val groupId = id.substringBefore(':')
    val groupState by remember(groupId, groupController) {
        groupController?.group(groupId) ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val channel = groupState?.channels?.firstOrNull { it.id == id.substringAfter(':', "") }
    val spaceController = demo as? SpaceController
    val isSpace = spaceController?.isSpace(id) == true
    val space by remember(id, spaceController) {
        spaceController?.space(id) ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)
    val readOnly = when {
        isSpace -> space?.kind == SpaceKind.Channel && space?.role != SpaceRole.Owner && space?.role != SpaceRole.Admin
        channel != null -> channel.adminsOnly && groupState?.isAdmin(groupController?.me.orEmpty()) != true
        else -> null
    }
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(app.blesk.feature.chats.R.string.media_failed)
    val context = LocalContext.current
    val notify = rememberNotify()
    // Picked photos and videos wait for «Сжатое» or «Оригинал» before they are encoded and sent.
    var picked by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val pickMedia =
        rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(Attachment.Album.MAX)) { uris ->
            picked = uris.take(Attachment.Album.MAX)
        }
    fun sendPicked(original: Boolean) {
        val uris = picked
        picked = emptyList()
        scope.launch {
            busy = true
            val items = uris.mapNotNull { uri -> importVisual(container, context.contentResolver.getType(uri), uri, original) }
            busy = false
            when {
                items.size < uris.size && items.isEmpty() -> notify(failed)
                items.size == 1 -> demo.sendAttachment(id, items.single())
                else -> demo.sendAttachment(id, Attachment.Album(items))
            }
            if (items.size < uris.size && items.isNotEmpty()) notify(failed)
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val attachment = container.media.importFile(uri)
            busy = false
            if (attachment != null) demo.sendAttachment(id, attachment) else notify(failed)
        }
    }
    // The camera app writes the shot to a scratch file, which is moved into the vault and deleted at once.
    val shot = remember { File(File(context.cacheDir, "camera").apply { mkdirs() }, "shot.jpg") }
    // Resolved when the camera opens, not on every chat: the provider checks the path against its roots.
    val shotUri by lazy { FileProvider.getUriForFile(context, "${context.packageName}.files", shot) }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { taken ->
        if (!taken) {
            shot.delete()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            busy = true
            val attachment = importVisual(container, "image/jpeg", shotUri)
            shot.delete()
            busy = false
            if (attachment != null) demo.sendAttachment(id, attachment) else notify(failed)
        }
    }
    val cameraDenied = stringResource(app.blesk.feature.chats.R.string.media_camera_denied)
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            container.lock.expectReturn()
            takePicture.launch(shotUri)
        } else {
            notify(cameraDenied)
        }
    }
    val playback = container.audioPlayer.playback.collectAsStateWithLifecycle()
    DisposableEffect(Unit) { onDispose { container.audioPlayer.stop() } }
    val mediaController = remember(id) {
        MediaController(
            playback = playback,
            onToggleVoice = container.audioPlayer::toggle,
            onSeekVoice = container.audioPlayer::seek,
            onCycleSpeed = container.audioPlayer::cycleSpeed,
            onOpenMedia = { message, index -> onOpenMedia(message.id, index) },
            onOpenFile = { file -> openFile(context, file) },
            onCancelTransfer = { message -> demo.cancelTransfer(id, message.id) },
            onRetryTransfer = { message -> demo.retryDownload(id, message.id) },
        )
    }
    val transfers by remember(demo) { demo.transfers }.collectAsStateWithLifecycle()
    val recorders = remember(id) {
        Recorders(
            voice = container.voiceRecorder,
            circle = container.circleRecorder,
            onVoice = { demo.sendAttachment(id, it) },
            onCircle = { demo.sendAttachment(id, it) },
        )
    }

    val direct = conversation?.kind == ConversationKind.Direct
    val keys = demo as? KeyVerification
    val keyState by remember(id, keys) { keys?.keyState(id) ?: flowOf(KeyState(false, false)) }
        .collectAsStateWithLifecycle(initialValue = KeyState(false, false))
    val bots = demo as? BotController
    val isBot = bots?.isBot(id) == true
    val commands by produceState(emptyList<BotCommand>(), id, isBot) {
        if (isBot && bots != null) value = bots.commands(id)
    }
    LaunchedEffect(bots) {
        bots?.notices?.collect { notify(it) }
    }
    val callbacks = remember(id, direct) {
        ConversationCallbacks(
            onSend = { text, replyTo -> demo.send(id, text, replyTo) },
            onOpenPerson = { username -> scope.launch { demo.openDirect(username)?.let(onOpenPerson) } },
            onEdit = { messageId, text -> demo.edit(id, messageId, text) },
            onDelete = { messageId -> demo.delete(id, messageId) },
            onReact = { messageId, symbol -> demo.toggleReaction(id, messageId, symbol) },
            onApplyTheme = { spec -> scope.launch { container.settings.updateAppearance { it.copy(theme = spec) } } },
            onOpenChatTheme = { themeSheet = true },
            onPickMedia = {
                container.lock.expectReturn()
                pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            },
            onPickFile = {
                container.lock.expectReturn()
                pickFile.launch(arrayOf("*/*"))
            },
            onTakePhoto = { cameraPermission.launch(Manifest.permission.CAMERA) },
            onPickMusic = {
                container.lock.expectReturn()
                pickFile.launch(arrayOf("audio/*"))
            },
            onTyping = { active -> demo.setTyping(id, active) },
            onOpenInfo = when {
                isSpace -> ({ onOpenSpace(id.removePrefix(SPACE_PREFIX).substringBefore(':')) })
                direct -> onVerify
                groupController != null && id.length > MAX_USERNAME_LENGTH -> ({ onOpenGroup(groupId) })
                else -> null
            },
            onCall = if (direct) onCall else null,
            onDraft = { text -> demo.saveDraft(id, text) },
            onForward = onForward,
            onPin = { messageId, pinned -> demo.pinMessage(id, messageId, pinned) },
            onButton = { messageId, button ->
                val link = button.url
                val data = button.data
                when {
                    link != null -> openLink(context, link)
                    data != null -> bots?.press(id, messageId, data)
                }
            },
            onRetry = { messageId -> demo.retry(id, messageId) },
            onReconnect = demo::reconnect,
            onUnblock = if (direct) ({ demo.setBlocked(demo.contact(id)?.username ?: id, false) }) else null,
            request = if (direct) requests else null,
            onEmojiPicked = { emoji -> scope.launch { container.settings.pickEmoji(emoji) } },
            onBack = onBack,
        )
    }

    val current = conversation ?: return
    val messages = (loaded ?: return).filter { it.id !in container.notices.hidden }
    val unread = unreadAtOpen ?: return
    BleskTheme(appearance = if (override != null) appearance.copy(theme = override) else appearance) {
        Box(Modifier.fillMaxSize()) {
            ConversationScreen(
                conversation = current,
                // Read receipts work both ways: switched off, you do not see others read yours either.
                messages = if (privacy.readReceipts) {
                    messages.toImmutableList()
                } else {
                    messages.map { if (it.status == SendStatus.Read) it.copy(status = SendStatus.Delivered) else it }.toImmutableList()
                },
                typing = id in typing,
                authorName = { authorId -> demo.contact(authorId)?.name },
                callbacks = callbacks,
                recorders = if (isBot) null else recorders,
                commands = commands.toImmutableList(),
                media = mediaController,
                busy = busy,
                readOnly = readOnly ?: (current.kind == ConversationKind.Channel),
                reactions = !isSpace && !isBot,
                demo = demo is DemoRepository,
                keyChanged = direct && keyState.changed,
                me = groupController?.me,
                unreadAtOpen = unread,
                focusMessageId = focusMessageId,
                connection = connection,
                sendByEnter = sendByEnter,
                doubleTapReaction = doubleTapReaction,
                blocked = direct && blockedPeople.any { it.id == id || it.username == id },
                presence = if (direct) presence[id] else null,
                hintsSeen = hintsSeen,
                onHintDone = { hint -> scope.launch { container.settings.markHint(hint) } },
                recentEmoji = recentEmoji.toImmutableList(),
                members = groupState?.members.orEmpty()
                    .filter { it.username != groupController?.me }
                    .map { member -> demo.contact(member.username) ?: Contact(member.username, member.username, member.username) }
                    .toImmutableList(),
                underlay = { ChatBackdrop(chatBackground(background, wallpaper != null), wallpaper) },
                transfers = transfers,
            )
            if (themeSheet) {
                ChatThemeSheet(
                    current = override ?: appearance.theme,
                    overridden = override != null,
                    onSelect = { spec -> scope.launch { container.settings.setChatTheme(id, spec) } },
                    onSend = {
                        demo.sendTheme(id, override ?: appearance.theme, caption)
                        themeSheet = false
                    },
                    onDismiss = { themeSheet = false },
                )
            }
            if (picked.isNotEmpty()) {
                val originalBytes = remember(picked) { picked.sumOf { uri -> sizeOf(context, uri) } }
                val compressedBytes by produceState(originalBytes, picked) {
                    value = withContext(Dispatchers.IO) { picked.sumOf { uri -> compressedSizeOf(context, uri) } }
                }
                QualitySheet(
                    count = picked.size,
                    originalBytes = originalBytes,
                    compressedBytes = compressedBytes,
                    onSend = ::sendPicked,
                    onDismiss = { picked = emptyList() },
                )
            }
        }
    }
}

/**
 * Roughly what «Сжатое» comes to: a photo at 2560 px in JPEG takes about a byte for every eight
 * pixels, a video at 720p about two and a half megabits a second; never more than the original.
 */
private fun compressedSizeOf(context: Context, uri: Uri): Long {
    val original = sizeOf(context, uri)
    val mime = context.contentResolver.getType(uri).orEmpty()
    val estimate = if (mime.startsWith("video/")) {
        val retriever = MediaMetadataRetriever()
        val millis = runCatching {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        }.getOrNull().also { retriever.release() } ?: return original
        millis * VIDEO_BITS_PER_SECOND / BITS_PER_BYTE / MILLIS_PER_SECOND
    } else {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching { context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } }
        val longest = maxOf(bounds.outWidth, bounds.outHeight).takeIf { it > 0 } ?: return original
        val scale = minOf(1.0, PHOTO_SIDE / longest)
        (bounds.outWidth * scale * bounds.outHeight * scale / PIXELS_PER_BYTE).toLong()
    }
    return if (original > 0) minOf(original, estimate) else estimate
}

private const val VIDEO_BITS_PER_SECOND = 2_500_000L
private const val BITS_PER_BYTE = 8L
private const val MILLIS_PER_SECOND = 1_000L
private const val PHOTO_SIDE = 2560.0
private const val PIXELS_PER_BYTE = 8.0

/** The size a picked file reports, or 0 if it does not say. */
private fun sizeOf(context: Context, uri: Uri): Long = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getLong(0) else 0L
    } ?: 0L
}.getOrDefault(0L)

private const val SPACE_PREFIX = "space:"

/** Group ids are UUIDs; usernames are at most 32 characters. */
private const val MAX_USERNAME_LENGTH = 32

/** Keeps text readable over any photo without hiding it. */
