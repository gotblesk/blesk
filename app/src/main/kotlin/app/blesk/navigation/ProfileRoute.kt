package app.blesk.navigation

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.design.component.headerPhotoKey
import app.blesk.feature.profile.AvatarCrop
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
    val headerPhoto = rememberHeaderPhoto(container, current.headerVersion)
    // A new avatar is framed first; only the square under the circle is kept.
    var cropping by remember { mutableStateOf<Bitmap?>(null) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { cropping = container.profilePhoto.decode(uri) }
    }
    val pickHeader = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (container.headerPhoto.import(uri)) {
                container.settings.bumpProfileHeader()
                container.settings.setProfileHeader(headerPhotoKey(0f, 0f))
            }
        }
    }
    val source = cropping
    if (source != null) {
        AvatarCrop(
            source = source,
            onDone = { square ->
                cropping = null
                scope.launch { if (container.profilePhoto.save(square)) container.settings.bumpProfilePhoto() }
            },
            onCancel = { cropping = null },
        )
        return
    }
    ProfileScreen(
        profile = current,
        photo = photo,
        headerPhoto = headerPhoto,
        actions = ProfileActions(
            onSave = { draft ->
                scope.launch {
                    container.settings.updateProfile(draft.name, draft.username, draft.bio)
                    container.settings.setProfileHeader(draft.header)
                }
            },
            onPickPhoto = {
                container.lock.expectReturn()
                pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onRemovePhoto = {
                container.profilePhoto.clear()
                scope.launch { container.settings.bumpProfilePhoto() }
            },
            onPickHeaderPhoto = {
                container.lock.expectReturn()
                pickHeader.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
            onBack = onBack,
        ),
    )
}
