package app.blesk.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
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

/** Whether blesk holds the permission to show notifications, which Android asks the person for from version 13. */
internal fun notificationsGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/**
 * Whether Android keeps blesk's notifications from showing: the permission refused, or, on every version,
 * blesk's notifications turned off in Android's settings.
 */
internal fun notificationsBlocked(context: Context): Boolean =
    !notificationsGranted(context) || !NotificationManagerCompat.from(context).areNotificationsEnabled()

/**
 * Whether Android refused without showing its question. It stops asking once the permission is refused twice,
 * and an earlier blesk asked at every start, so this may be so before blesk ever asks. Then it offers to explain
 * neither before nor after the request; after a question it did show, it offers to either before (a second
 * refusal) or after (a first one).
 */
internal fun refusedWithoutAsking(granted: Boolean, explainBefore: Boolean, explainAfter: Boolean): Boolean =
    !granted && !explainBefore && !explainAfter

/** Android's page of blesk's notifications: there they can always be allowed, whatever was answered before. */
internal fun openNotificationSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
}

/** Whether the question about notifications is to be on screen now. */
@Composable
internal fun notificationAskShown(container: AppContainer): Boolean {
    val needed by container.notificationsNeeded.collectAsStateWithLifecycle()
    // Taken as asked until read, so the sheet never flashes up for someone who answered before.
    val asked by container.onboarding.notificationsAsked.collectAsStateWithLifecycle(initialValue = true)
    return notificationAskDue(Build.VERSION.SDK_INT, needed, asked, notificationsGranted(LocalContext.current))
}

/**
 * The question about notifications, once, when they are first needed: the reason in one line first, then
 * Android's own question. Either answer is final; after a refusal «Уведомления» shows how to allow them.
 * Kept in place while not [shown], so that Android's answer still comes back here.
 */
@Composable
internal fun NotificationAsk(container: AppContainer, shown: Boolean) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var explainBefore by rememberSaveable { mutableStateOf(false) }
    val system = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // Android will not ask any more: «Разрешить» leads to its page of blesk's notifications instead of nowhere.
        if (refusedWithoutAsking(granted, explainBefore, explainAfter = activity?.offersToExplain() ?: true)) {
            openNotificationSettings(context)
        }
    }
    if (!shown) return
    NotificationAskSheet(
        onAllow = {
            explainBefore = activity?.offersToExplain() ?: false
            system.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onAnswer = { scope.launch { container.onboarding.markNotificationsAsked() } },
    )
}

private fun Activity.offersToExplain(): Boolean =
    ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.POST_NOTIFICATIONS)

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
