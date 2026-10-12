package app.blesk.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

private val QrPaper = Color(0xFFF4F1EC)
private val QrInk = Color(0xFF1D1F22)

class ProfileActions(
    val onSave: (name: String, username: String, bio: String) -> Unit,
    val onPickPhoto: () -> Unit,
    val onRemovePhoto: () -> Unit,
    val onBack: () -> Unit,
)

@Composable
fun ProfileScreen(profile: Profile, photo: ImageBitmap?, actions: ProfileActions, modifier: Modifier = Modifier) {
    val name = rememberTextFieldState(profile.name)
    val username = rememberTextFieldState(profile.username)
    val bio = rememberTextFieldState(profile.bio)
    var saves by remember { mutableStateOf(0) }
    val currentUsername = Profile.normalizeUsername(username.text.toString())
    val dirty = name.text.toString() != profile.name || currentUsername != profile.username || bio.text.toString() != profile.bio
    val photoLabel = stringResource(R.string.profile_photo)

    ScrollScreen(title = stringResource(R.string.profile_title), onBack = actions.onBack, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(
                name = name.text.toString().ifBlank { "?" },
                photo = photo,
                size = 112.dp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClickLabel = photoLabel, onClick = actions.onPickPhoto)
                    .semantics { contentDescription = photoLabel },
            )
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(stringResource(R.string.profile_photo), onClick = actions.onPickPhoto, kind = ButtonKind.Secondary)
                if (photo != null) {
                    Button(stringResource(R.string.profile_photo_remove), onClick = actions.onRemovePhoto, kind = ButtonKind.Secondary)
                }
            }
        }
        val haptics = rememberHaptics()
        Fields(name, username, bio)
        Button(
            text = stringResource(if (dirty || saves == 0) R.string.profile_save else R.string.profile_saved),
            onClick = {
                actions.onSave(name.text.toString(), username.text.toString(), bio.text.toString())
                haptics.success()
                saves++
            },
            modifier = Modifier
                .padding(horizontal = Blesk.dimens.gutter, vertical = 12.dp)
                .fillMaxWidth()
                .flashOn(saves, CircleShape),
        )

        SectionHeader(stringResource(R.string.profile_qr))
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            if (Profile.isValidUsername(profile.username)) {
                val description = stringResource(R.string.profile_qr_description, profile.username)
                Box(
                    Modifier
                        .padding(24.dp)
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(QrPaper)
                        .padding(24.dp)
                        .semantics { contentDescription = description },
                ) {
                    QrCode(profileLink(profile.username), QrInk, Modifier.fillMaxWidth().aspectRatio(1f))
                }
                Text(
                    text = "@${profile.username}",
                    style = Blesk.type.headline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.profile_qr_hint),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
            } else {
                Text(
                    text = stringResource(R.string.profile_qr_needs_username),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Fields(name: TextFieldState, username: TextFieldState, bio: TextFieldState) {
    TextInput(label = stringResource(R.string.profile_name), state = name)
    TextInput(
        label = stringResource(R.string.profile_username),
        state = username,
        hint = stringResource(R.string.profile_username_hint),
    )
    TextInput(label = stringResource(R.string.profile_bio), state = bio, singleLine = false)
}
