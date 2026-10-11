package app.blesk.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.KeyState
import app.blesk.core.data.KeyVerification
import app.blesk.core.data.SafetyCode
import app.blesk.feature.profile.QrCode
import app.blesk.feature.settings.SafetyScreen
import kotlinx.coroutines.launch

@Composable
internal fun SafetyRoute(container: AppContainer, username: String, onBack: () -> Unit) {
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val keys = chats as? KeyVerification ?: return
    val scope = rememberCoroutineScope()
    val code by produceState<SafetyCode?>(null, username) { value = keys.safetyCode(username) }
    val state by remember(username) { keys.keyState(username) }.collectAsStateWithLifecycle(initialValue = KeyState(false, false))
    SafetyScreen(
        name = chats.contact(username)?.name ?: username,
        code = code,
        state = state,
        onScanned = { scanned -> keys.verifyScanned(username, scanned) },
        onSetVerified = { verified -> scope.launch { keys.setVerified(username, verified) } },
        onBack = onBack,
        // Dark on white whatever the theme: scanners read that best.
        qr = { content -> QrCode(content, Color.Black, Modifier.fillMaxSize()) },
    )
}
