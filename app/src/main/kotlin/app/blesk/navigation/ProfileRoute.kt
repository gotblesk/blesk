package app.blesk.navigation

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.feature.profile.ProfileActions
import app.blesk.feature.profile.ProfileScreen
import kotlinx.coroutines.launch

@Composable
internal fun rememberProfilePhoto(container: AppContainer, version: Long): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, version) { value = container.profilePhoto.load()?.asImageBitmap() }
    return photo
}

@Composable
internal fun rememberHeaderPhoto(container: AppContainer, version: Long): ImageBitmap? {
    val photo by produceState<ImageBitmap?>(null, version) { value = container.headerPhoto.load()?.asImageBitmap() }
    return photo
}

@Composable
internal fun ProfileRoute(container: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
    val current = profile ?: return
    val photo = rememberProfilePhoto(container, current.photoVersion)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (container.profilePhoto.import(uri)) container.settings.bumpProfilePhoto()
        }
    }
    ProfileScreen(
        profile = current,
        photo = photo,
        actions = ProfileActions(
            onSave = { name, username, bio -> scope.launch { container.settings.updateProfile(name, username, bio) } },
            onPickPhoto = {
                container.lock.expectReturn()
                pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = {
                container.profilePhoto.clear()
                scope.launch { container.settings.bumpProfilePhoto() }
            },
            onBack = onBack,
        ),
    )
}
