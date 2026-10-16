package app.blesk.locks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockCode
import app.blesk.feature.chats.locks.ChatLocksActions
import app.blesk.feature.chats.locks.ChatLocksScreen
import app.blesk.feature.security.PinSetup
import kotlinx.coroutines.launch

/** «Замки на чаты» in privacy settings. Choosing a separate code that does not exist yet makes it first. */
@Composable
fun ChatLocksRoute(container: AppContainer, onSeparateCode: () -> Unit, onNeedCode: () -> Unit, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    ChatLocksScreen(
        rules = locks.rules,
        lockedChats = (locks.locked - ChatLocks.ARCHIVE).size,
        archiveLocked = locks.isLocked(ChatLocks.ARCHIVE),
        separateCodeSet = container.lockVault.isSet,
        actions = ChatLocksActions(
            onRules = { rules ->
                scope.launch { container.chatLocks.update { it.copy(rules = rules) } }
                if (rules.code == LockCode.Separate && !container.lockVault.isSet) onSeparateCode()
            },
            onArchive = { lock -> container.setLocked(ChatLocks.ARCHIVE, lock, onNeedCode) },
            onSeparateCode = onSeparateCode,
            onBack = onBack,
        ),
    )
}

/** The separate code for locks: set or replaced; the app's own code stays as it is. */
@Composable
fun LockCodeRoute(container: AppContainer, onDone: () -> Unit) {
    PinSetup(
        minPassword = PinVault.MIN_PASSWORD,
        onSave = { code -> container.lockVault.set(code) },
        onDone = onDone,
        onCancel = onDone,
    )
}
