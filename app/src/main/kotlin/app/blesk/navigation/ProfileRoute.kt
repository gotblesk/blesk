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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.settings.AppMode
import app.blesk.core.design.component.LocalNotices
import app.blesk.core.design.component.headerPhotoKey
import app.blesk.core.network.ApiException
import app.blesk.feature.profile.AvatarCrop
import app.blesk.feature.profile.ProfileActions
import app.blesk.feature.profile.ProfileScreen
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException

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
internal fun ProfileRoute(container: AppContainer, onBack: () -> Unit, onReopen: () -> Unit) {
    val scope = rememberCoroutineScope()
    val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
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
    val notices = LocalNotices.current
    val unsaved = stringResource(app.blesk.feature.profile.R.string.profile_unsaved)
    // A draft brought back by «Отменить» is taken once; the next opening starts from the saved profile.
    val restored = remember { container.profileDraft.value.also { container.profileDraft.value = null } }
    ProfileScreen(
        profile = current,
        restored = restored,
        photo = photo,
        headerPhoto = headerPhoto,
        actions = ProfileActions(
            onSave = { draft ->
                scope.launch {
                    container.settings.updateProfile(draft.name, draft.username, draft.bio, draft.links)
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
            checkUsername = { name ->
                val api = container.api
                if (mode is AppMode.Server && api != null) {
                    // Only failures to ask are unknown; a cancelled check must stay cancelled.
                    try {
                        api.checkUsername(name).available
                    } catch (_: IOException) {
                        null
                    } catch (_: ApiException) {
                        null
                    }
                } else {
                    // The demo knows only its own people.
                    container.chats.value?.contacts?.first()?.none { it.username == name } ?: true
                }
            },
            usernameEditable = mode !is AppMode.Server,
            onLeaveUnsaved = { draft ->
                onBack()
                notices?.undoable(
                    unsaved,
                    restore = {
                        container.profileDraft.value = draft
                        onReopen()
                    },
                ) {}
            },
        ),
    )
}
