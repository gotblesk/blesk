package app.blesk.feature.chats.media

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.MenuSheet
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.R

/**
 * After a refusal the system no longer asks again, so the only way back to recording is the app's
 * page in the settings; this sheet says what is missing and takes you there.
 */
@Composable
fun RecordPermissionSheet(mode: RecordMode, onDismiss: () -> Unit) {
    val context = LocalContext.current
    MenuSheet(title = stringResource(R.string.media_permission_title), onDismiss = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val reason = if (mode == RecordMode.Voice) R.string.media_permission_mic_denied else R.string.media_permission_camera_denied
                Text(
                    stringResource(reason),
                    color = Blesk.colors.textSecondary,
                )
                Button(
                    text = stringResource(R.string.media_permission_settings),
                    icon = BleskIcons.Settings,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        onDismiss()
                        val page = Uri.fromParts("package", context.packageName, null)
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, page)
                        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                )
                Button(
                    text = stringResource(R.string.media_permission_later),
                    kind = ButtonKind.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss,
                )
            }
        }
    }
}
