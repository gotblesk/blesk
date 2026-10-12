package app.blesk.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.chatBackdropKey
import app.blesk.core.design.component.chatBackdropOf
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.settings.AppearanceFileActions
import app.blesk.feature.settings.AppearanceScreen
import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

@Composable
internal fun AppearanceRoute(container: AppContainer, appearance: Appearance, onBack: () -> Unit) {
    val context = LocalContext.current
    val notify = rememberNotify()
    val scope = rememberCoroutineScope()
    var hasWallpaper by remember { mutableStateOf(container.wallpaper.exists) }
    val background by container.settings.chatBackground.collectAsStateWithLifecycle(initialValue = null)
    val resolver = context.contentResolver
    val failed = context.getString(app.blesk.feature.settings.R.string.appearance_open_failed)

    fun change(next: Appearance) {
        if (next.theme != appearance.theme) container.eventLog.record(EventCode.ThemeChanged, next.theme.preset.ordinal.toLong())
        scope.launch { container.settings.updateAppearance { next } }
    }

    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (container.wallpaper.import(uri)) {
                hasWallpaper = true
                container.settings.bumpWallpaper()
                container.settings.setChatBackground(chatBackdropKey(HeaderStyle.Photo))
            }
        }
    }
    val saveTheme = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(ThemeFile.MIME)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = ThemeFile(theme = appearance.theme).encode()
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    resolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) } != null
                } catch (_: IOException) {
                    false
                }
            }
            if (ok) notify(context.getString(R.string.theme_saved))
        }
    }
    val openTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                try {
                    resolver.openInputStream(uri)?.use { it.readBytes(MAX_THEME_BYTES) }?.decodeToString()?.let(ThemeFile::decode)
                } catch (_: IOException) {
                    null
                }
            }
            if (file == null) {
                notify(failed)
            } else {
                change(appearance.copy(theme = file.theme))
                notify(context.getString(R.string.theme_opened))
            }
        }
    }

    AppearanceScreen(
        appearance = appearance,
        onChange = ::change,
        files = AppearanceFileActions(
            hasWallpaper = hasWallpaper,
            background = chatBackground(background, hasWallpaper),
            onBackground = { style -> scope.launch { container.settings.setChatBackground(chatBackdropKey(style)) } },
            onPickWallpaper = {
                container.lock.expectReturn()
                pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemoveWallpaper = {
                container.wallpaper.clear()
                hasWallpaper = false
                scope.launch {
                    container.settings.bumpWallpaper()
                    val showingPhoto = chatBackdropOf(background.orEmpty()) == HeaderStyle.Photo
                    if (showingPhoto) container.settings.setChatBackground(chatBackdropKey(null))
                }
            },
            onSaveTheme = {
                container.lock.expectReturn()
                saveTheme.launch("blesk.${ThemeFile.EXTENSION}")
            },
            onOpenTheme = {
                container.lock.expectReturn()
                openTheme.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
            },
        ),
        onBack = onBack,
    )
}

private const val MAX_THEME_BYTES = 8 * 1024

private fun java.io.InputStream.readBytes(limit: Int): ByteArray {
    val buffer = ByteArray(limit)
    var total = 0
    while (total < limit) {
        val read = read(buffer, total, limit - total)
        if (read < 0) break
        total += read
    }
    return buffer.copyOf(total)
}

/** Before anything is chosen, a photo picked in an older version keeps showing. */
internal fun chatBackground(stored: String?, hasPhoto: Boolean): HeaderStyle? = when {
    stored != null -> chatBackdropOf(stored).takeUnless { it == HeaderStyle.Photo && !hasPhoto }
    hasPhoto -> HeaderStyle.Photo
    else -> null
}
