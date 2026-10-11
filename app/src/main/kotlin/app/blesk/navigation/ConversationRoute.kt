package app.blesk.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.model.SendStatus
import app.blesk.core.data.settings.Privacy
import app.blesk.R
import app.blesk.core.design.theme.Blesk
import app.blesk.core.design.theme.BleskTheme
import app.blesk.feature.chats.ConversationCallbacks
import app.blesk.feature.chats.ConversationScreen
import app.blesk.shared.appearance.Appearance
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Composable
internal fun ConversationRoute(container: AppContainer, id: String, appearance: Appearance, onBack: () -> Unit) {
    val demo = container.demo
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

    val callbacks = remember(id) {
        ConversationCallbacks(
            onSend = { text, replyTo -> demo.send(id, text, replyTo) },
            onEdit = { messageId, text -> demo.edit(id, messageId, text) },
            onDelete = { messageId -> demo.delete(id, messageId) },
            onReact = { messageId, symbol -> demo.toggleReaction(id, messageId, symbol) },
            onApplyTheme = { spec -> scope.launch { container.settings.updateAppearance { it.copy(theme = spec) } } },
            onOpenChatTheme = { themeSheet = true },
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

/** Keeps text readable over any photo without hiding it. */
private const val WALLPAPER_SCRIM = 0.45f
