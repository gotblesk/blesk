package app.blesk.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.blesk.feature.settings.StorageScreen
import app.blesk.feature.settings.StorageUse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun StorageRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableIntStateOf(0) }
    val use by produceState<StorageUse?>(null, version) { value = withContext(Dispatchers.IO) { measure(context) } }
    StorageScreen(
        use = use,
        onClearCache = {
            scope.launch {
                withContext(Dispatchers.IO) { context.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
                version++
            }
        },
        onBack = onBack,
    )
}

private fun measure(context: Context) = StorageUse(
    media = File(context.filesDir, "media").sizeOnDisk(),
    database = context.getDatabasePath("blesk.db").parentFile?.sizeOnDisk() ?: 0L,
    cache = context.cacheDir.sizeOnDisk(),
)

private fun File.sizeOnDisk(): Long = walkBottomUp().filter { it.isFile }.sumOf { it.length() }
