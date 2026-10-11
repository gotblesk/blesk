package app.blesk.navigation

import android.widget.Toast
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
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.diagnostics.EventCode
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
    val scope = rememberCoroutineScope()
    var hasWallpaper by remember { mutableStateOf(container.wallpaper.exists) }
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
            if (ok) Toast.makeText(context, R.string.theme_saved, Toast.LENGTH_SHORT).show()
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
                Toast.makeText(context, failed, Toast.LENGTH_SHORT).show()
            } else {
                change(appearance.copy(theme = file.theme))
                Toast.makeText(context, R.string.theme_opened, Toast.LENGTH_SHORT).show()
            }
        }
    }

    AppearanceScreen(
        appearance = appearance,
        onChange = ::change,
        files = AppearanceFileActions(
            hasWallpaper = hasWallpaper,
            onPickWallpaper = { pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemoveWallpaper = {
                container.wallpaper.clear()
                hasWallpaper = false
                scope.launch { container.settings.bumpWallpaper() }
            },
            onSaveTheme = { saveTheme.launch("blesk.${ThemeFile.EXTENSION}") },
            onOpenTheme = { openTheme.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
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
