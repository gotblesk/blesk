package app.blesk.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import kotlinx.coroutines.launch

/**
 * Whether to ask for notifications now: Android asks the person itself from version 13, so blesk asks once,
 * at the first moment they are [needed], and not when they are [granted] already.
 */
internal fun notificationAskDue(sdk: Int, needed: Boolean, asked: Boolean, granted: Boolean): Boolean =
    sdk >= Build.VERSION_CODES.TIRAMISU && needed && !asked && !granted

/** Whether Android lets blesk show notifications; below version 13 it always does unless they are turned off. */
internal fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * The question about notifications, once, when they are first needed: the reason in one line first, then
 * Android's own question. Either answer is final; after a refusal «Уведомления» shows how to allow them.
 */
@Composable
internal fun NotificationAsk(container: AppContainer) {
    val needed by container.notificationsNeeded.collectAsStateWithLifecycle()
    // Taken as asked until read, so the sheet never flashes up for someone who answered before.
    val asked by container.onboarding.notificationsAsked.collectAsStateWithLifecycle(initialValue = true)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val system = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    if (!notificationAskDue(Build.VERSION.SDK_INT, needed, asked, notificationsGranted(context))) return
    NotificationAskSheet(
        onAllow = { system.launch(Manifest.permission.POST_NOTIFICATIONS) },
        onAnswer = { scope.launch { container.onboarding.markNotificationsAsked() } },
    )
}

/** «Разрешить» hands over to Android's question; «Отмена» is no. [onAnswer] comes with either. */
@Composable
internal fun NotificationAskSheet(onAllow: () -> Unit, onAnswer: () -> Unit) {
    ChoiceSheet(
        title = stringResource(R.string.notify_ask_title),
        text = stringResource(R.string.notify_ask_text),
        choices = listOf(Choice(stringResource(R.string.notify_allow), onClick = onAllow)),
        onDismiss = onAnswer,
    )
}
