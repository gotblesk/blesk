package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.feature.settings.UpdateProgress
import app.blesk.feature.settings.UpdateScreen
import app.blesk.update.UpdateFailure
import kotlinx.coroutines.launch

@Composable
internal fun UpdateRoute(container: AppContainer, onBack: () -> Unit) {
    val update by container.updater.available.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<UpdateProgress>(UpdateProgress.Idle) }
    val found = update
    if (found == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    UpdateScreen(
        title = found.title,
        // The CI adds the branch and commit under the notes; that line means nothing to a reader.
        notes = found.notes.lines().filterNot { it.startsWith(BRANCH_LINE) }.joinToString("\n"),
        progress = progress,
        onInstall = {
            if (progress is UpdateProgress.Downloading) return@UpdateScreen
            progress = UpdateProgress.Downloading(0f)
            scope.launch {
                val failure = container.updater.install(found) { progress = UpdateProgress.Downloading(it) }
                progress = when (failure) {
                    null -> UpdateProgress.Handed
                    UpdateFailure.Network -> UpdateProgress.NetworkFailed
                    UpdateFailure.Signature -> UpdateProgress.Forged
                }
            }
        },
        onBack = onBack,
    )
}

private const val BRANCH_LINE = "Ветка:"
