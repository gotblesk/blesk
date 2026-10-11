package app.blesk.navigation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.GroupController
import app.blesk.core.data.SpaceController
import app.blesk.core.data.model.ConversationKind
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Privacy
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskTheme
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.chats.media.MediaController
import app.blesk.feature.chats.media.Recorders
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@Composable
internal fun ConversationRoute(
    container: AppContainer,
    id: String,
    appearance: Appearance,
    onOpenMedia: (messageId: String) -> Unit,
    onOpenGroup: (groupId: String) -> Unit,
    onOpenSpace: (handle: String) -> Unit,
    onBack: () -> Unit,
) {
    val demo = container.chats.collectAsStateWithLifecycle().value ?: return
    val scope = rememberCoroutineScope()
    val conversation by demo.conversation(id).collectAsStateWithLifecycle(initialValue = null)
    val messages by demo.messages(id).collectAsStateWithLifecycle(initialValue = emptyList())
    val typing by demo.typing.collectAsStateWithLifecycle()
    val privacy by container.settings.privacy.collectAsStateWithLifecycle(initialValue = Privacy())
    val chatThemes by container.settings.chatThemes.collectAsStateWithLifecycle(initialValue = emptyMap())
    val wallpaperVersion by container.settings.wallpaperVersion.collectAsStateWithLifecycle(initialValue = 0L)
    val wallpaper by produceState<ImageBitmap?>(null, wallpaperVersion) {
        value = container.wallpaper.load()?.asImageBitmap()
    }
    var themeSheet by remember { mutableStateOf(false) }
    val override = chatThemes[id]
    val caption = stringResource(R.string.chat_theme_caption)
    LaunchedEffect(id) { demo.markRead(id) }
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
    val context = LocalContext.current
    var busy by remember { mutableStateOf(false) }
    val failed = stringResource(app.blesk.feature.chats.R.string.media_failed)
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val attachment = importVisual(container, context.contentResolver.getType(uri), uri)
            busy = false
            if (attachment != null) demo.sendAttachment(id, attachment) else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val attachment = container.media.importFile(uri)
            busy = false
            if (attachment != null) demo.sendAttachment(id, attachment) else Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
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
            onOpenMedia = { onOpenMedia(it.id) },
            onOpenFile = { file -> openFile(context, file) },
        )
    }
    val recorders = remember(id) {
        Recorders(
            voice = container.voiceRecorder,
            circle = container.circleRecorder,
            onVoice = { demo.sendAttachment(id, it) },
            onCircle = { demo.sendAttachment(id, it) },
        )
    }

    val callbacks = remember(id) {
        ConversationCallbacks(
            onSend = { text, replyTo -> demo.send(id, text, replyTo) },
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
            onTyping = { active -> demo.setTyping(id, active) },
            onOpenInfo = when {
                isSpace -> ({ onOpenSpace(id.removePrefix(SPACE_PREFIX).substringBefore(':')) })
                groupController != null && id.length > MAX_USERNAME_LENGTH -> ({ onOpenGroup(groupId) })
                else -> null
            },
            onBack = onBack,
        )
    }

    val current = conversation ?: return
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
                recorders = recorders,
                media = mediaController,
                busy = busy,
                readOnly = readOnly ?: (current.kind == ConversationKind.Channel),
                reactions = !isSpace,
                underlay = {
                    wallpaper?.let { image ->
                        Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Blesk.colors.background.copy(alpha = WALLPAPER_SCRIM)))
                    }
                },
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
        }
    }
}

private const val SPACE_PREFIX = "space:"

/** Group ids are UUIDs; usernames are at most 32 characters. */
private const val MAX_USERNAME_LENGTH = 32

/** Keeps text readable over any photo without hiding it. */
private const val WALLPAPER_SCRIM = 0.45f
