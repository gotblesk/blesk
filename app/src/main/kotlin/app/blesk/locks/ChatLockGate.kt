package app.blesk.locks

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.crypto.Biometrics
import app.blesk.core.crypto.PinVault
import app.blesk.core.data.settings.ChatLocks
import app.blesk.core.data.settings.LockCode
import app.blesk.core.data.settings.LockOverrides
import app.blesk.core.data.settings.LockRules
import app.blesk.core.data.settings.LockUnlock
import app.blesk.core.design.component.blockTaps
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.locks.ChatLockControls
import app.blesk.feature.chats.locks.LocalChatLockControls
import app.blesk.feature.chats.locks.LockGlass
import app.blesk.feature.security.PinAttempt
import app.blesk.feature.security.PinEntry
import app.blesk.navigation.toAttempt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import app.blesk.feature.chats.R as ChatsR
import app.blesk.feature.security.R as SecurityR

/** The code that opens locks under [rules]: the separate one when chosen and set, otherwise the app's. */
fun AppContainer.vaultFor(rules: LockRules): PinVault =
    if (rules.code == LockCode.Separate && lockVault.isSet) lockVault else pinVault

/**
 * Shows [content] — a chat, a room's channel, «Избранное» or the archive ([ChatLocks.ARCHIVE]) —
 * behind its lock, when it has one.
 * The fingerprint is asked for straight away where the rules allow it; the code is one tap away.
 * Once opened, the frost thaws from the centre and the chat stays open until its rules close it
 * again after leaving. With no code set at all there is nothing to check against, so the lock is
 * not in the way.
 */
@Composable
fun ChatLockGate(container: AppContainer, conversationId: String, content: @Composable () -> Unit) {
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = null)
    val open by container.lockSession.open.collectAsStateWithLifecycle()
    val current = locks ?: return
    val lockId = current.lockOf(conversationId)
    if (lockId == null) {
        content()
        return
    }
    val rules = current.rulesFor(lockId)
    val vault = container.vaultFor(rules)
    if (!vault.isSet) {
        content()
        return
    }
    LockedContent(container, lockId, rules, vault, lockId in open, content)
}

/** For a chat, a room's channel, or the archive: which lock, if any, covers it. */
fun ChatLocks.lockOf(conversationId: String): String? = when {
    isLocked(conversationId) -> conversationId
    isLocked(conversationId.substringBefore(':')) -> conversationId.substringBefore(':')
    else -> null
}

@Composable
private fun LockedContent(
    container: AppContainer,
    lockId: String,
    rules: LockRules,
    vault: PinVault,
    open: Boolean,
    content: @Composable () -> Unit,
) {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val motion = Blesk.motion
    val thaw = remember(lockId) { Animatable(if (open) 1f else 0f) }
    var typing by remember(lockId) { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    val title = stringResource(SecurityR.string.lock_biometric_prompt)
    val cancel = stringResource(SecurityR.string.lock_biometric_cancel)
    val canUseBiometric = rules.unlock == LockUnlock.CodeOrBiometric &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Biometrics.available(context) && activity != null
    fun opened() {
        typing = false
        container.lockSession.opened(lockId)
        haptics.success()
        scope.launch { thaw.animateTo(1f, if (motion.reduced) tween(0) else tween(THAW_MILLIS)) }
    }
    val biometric: (() -> Unit)? = if (canUseBiometric && activity != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        { Biometrics.prompt(activity, title, cancel) { opened() } }
    } else {
        null
    }
    LaunchedEffect(lockId) { if (!open) biometric?.invoke() }
    // Closed from elsewhere (the app went to the background): freeze again at once.
    LaunchedEffect(open) { if (!open && thaw.value > 0f) thaw.snapTo(0f) }
    val relock by rememberUpdatedState(rules.relock)
    DisposableEffect(lockId) { onDispose { if (container.lockSession.isOpen(lockId)) container.lockSession.left(lockId, relock) } }
    LaunchedEffect(dimmed) {
        if (dimmed) {
            delay(DIM_MILLIS)
            dimmed = false
        }
    }
    LockGlass(thaw = thaw.value, dimmed = dimmed, onBiometric = biometric, onCode = { typing = true }) { content() }
    if (typing) {
        BackHandler { typing = false }
        Box(Modifier.fillMaxSize().background(Blesk.colors.background).blockTaps()) {
            PinEntry(
                title = stringResource(ChatsR.string.lock_closed),
                length = vault.length,
                check = { code ->
                    vault.check(code).toAttempt().also { if (it != PinAttempt.Ok) dimmed = true }
                },
                onOpened = ::opened,
                onBiometric = biometric,
            )
        }
    }
}

private const val THAW_MILLIS = 700
private const val DIM_MILLIS = 900L

/** Gives the details of [conversationId] their «Замок»: lock, unlock and rules of its own. */
@Composable
fun ProvideChatLock(container: AppContainer, conversationId: String, onNeedCode: () -> Unit, content: @Composable () -> Unit) {
    val locks by container.chatLocks.locks.collectAsStateWithLifecycle(initialValue = ChatLocks())
    val chats = container.chats.collectAsStateWithLifecycle().value
    val conversation by remember(chats, conversationId) { chats?.conversation(conversationId) ?: flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val controls = ChatLockControls(
        title = conversation?.title.orEmpty(),
        locked = locks.isLocked(conversationId),
        own = locks.overrides[conversationId] ?: LockOverrides(),
        onLock = { lock -> container.setLocked(conversationId, lock, onNeedCode) },
        onOwn = { own -> scope.launch { container.chatLocks.update { it.copy(overrides = it.overrides + (conversationId to own)) } } },
    )
    CompositionLocalProvider(LocalChatLockControls provides controls, content = content)
}

/** A chat's details: behind the chat's lock, with «Замок» among them. */
@Composable
fun LockedDetails(container: AppContainer, conversationId: String, onNeedCode: () -> Unit, content: @Composable () -> Unit) {
    ChatLockGate(container, conversationId) { ProvideChatLock(container, conversationId, onNeedCode, content) }
}
