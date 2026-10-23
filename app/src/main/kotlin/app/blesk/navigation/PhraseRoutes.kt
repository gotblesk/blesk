package app.blesk.navigation

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CredentialManager
import androidx.credentials.exceptions.CreateCredentialException
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.settings.AppMode
import app.blesk.core.design.component.rememberNotify
import app.blesk.feature.security.PhraseActions
import app.blesk.feature.security.PhraseScreen
import app.blesk.feature.security.PinAttempt
import app.blesk.feature.security.PinEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import app.blesk.R as AppR
import app.blesk.feature.security.R as SecurityR

/** The recovery phrase, from the settings. */
@Serializable
data object PhraseKey : NavKey

/**
 * The phrase, behind the app's code when there is one, on a screen that cannot be captured.
 * Copying marks it sensitive and clears the clipboard a minute later. A [fresh] phrase, shown
 * as the account is set up, opens without the code. [onDone] follows its check, [onLater] puts
 * it off, offered only while setting up, and [onBack] leaves it unchecked.
 */
@Composable
internal fun PhraseRoute(
    container: AppContainer,
    fresh: Boolean,
    onDone: () -> Unit,
    onLater: (() -> Unit)?,
    onBack: (() -> Unit)?,
) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val notify = rememberNotify()
    var open by rememberSaveable { mutableStateOf(fresh || !container.pinVault.isSet) }
    val copied = stringResource(SecurityR.string.phrase_copied)
    val saved = stringResource(SecurityR.string.phrase_saved_to_manager)
    val label = stringResource(SecurityR.string.phrase_title)
    DisposableEffect(activity) {
        val window = activity?.window
        val alreadySecure = window != null && (window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { if (!alreadySecure) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    if (!open) {
        PinEntry(
            title = stringResource(SecurityR.string.phrase_unlock),
            length = container.pinVault.length,
            check = { pin -> if (container.pinVault.check(pin) == PinVault.Result.Ok) PinAttempt.Ok else PinAttempt.Wrong(0) },
            onOpened = { open = true },
        )
        return
    }
    val words by produceState<List<String>?>(null) { value = withContext(Dispatchers.IO) { container.phrase.words() } }
    val phrase = words ?: return
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = null)
    // How the entry is named in the password manager: the app and the account, never the words.
    val entry = listOfNotNull(stringResource(AppR.string.app_name), (mode as? AppMode.Server)?.username?.let { "@$it" }).joinToString(" ")
    PhraseScreen(
        words = phrase,
        checked = container.phrase.isChecked,
        actions = PhraseActions(
            onCopy = {
                copySensitive(context, label, phrase.joinToString(" "))
                notify(copied)
                container.scope.launch {
                    delay(CLIPBOARD_MILLIS)
                    clearClipboard(context)
                }
            },
            // Android 14 has the password managers built in; before it they come only through Google services, which blesk does without.
            onPasswordManager = activity?.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE }?.let { host ->
                {
                    scope.launch {
                        val request = CreatePasswordRequest(id = entry, password = phrase.joinToString(" "))
                        try {
                            CredentialManager.create(context).createCredential(host, request)
                            notify(saved)
                        } catch (_: CreateCredentialException) {
                            // Cancelled, or no manager on this phone: the other ways are still on the screen.
                        }
                    }
                }
            },
            onChecked = {
                scope.launch {
                    withContext(Dispatchers.IO) { container.phrase.markChecked() }
                    onDone()
                }
            },
            onLater = onLater,
            onBack = onBack,
        ),
    )
}

private fun copySensitive(context: Context, label: String, text: String) {
    val clip = ClipData.newPlainText(label, text)
    // Keyboards and clipboard previews then show dots instead of the words.
    clip.description.extras = PersistableBundle().apply {
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else SENSITIVE_KEY
        putBoolean(key, true)
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

private fun clearClipboard(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        clipboard.clearPrimaryClip()
    } else {
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
    }
}

private const val CLIPBOARD_MILLIS = 60_000L
private const val SENSITIVE_KEY = "android.content.extra.IS_SENSITIVE"
