package app.blesk.onboarding

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.data.settings.ProxySetting
import app.blesk.core.data.settings.StartState
import app.blesk.core.design.component.rememberUsernameChecks
import app.blesk.core.design.component.rememberUsernameInput
import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerTrouble
import app.blesk.core.motion.rememberHaptics
import app.blesk.core.network.ServerAddress
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.feature.onboarding.InviteStepActions
import app.blesk.feature.onboarding.InviteStepScreen
import app.blesk.feature.onboarding.InviteStepState
import app.blesk.feature.onboarding.KnownServer
import app.blesk.feature.onboarding.ServerStepActions
import app.blesk.feature.onboarding.ServerStepScreen
import app.blesk.feature.onboarding.ServerStepState
import app.blesk.feature.onboarding.UsernameStepActions
import app.blesk.feature.onboarding.UsernameStepScreen
import app.blesk.feature.onboarding.UsernameStepState
import app.blesk.feature.settings.ProxySection
import app.blesk.feature.settings.QrScanner
import app.blesk.navigation.HomeKey
import app.blesk.navigation.PolicyKey
import app.blesk.navigation.StackDirection
import app.blesk.navigation.TermsKey
import app.blesk.navigation.UpdateKey
import app.blesk.navigation.checkProxy
import app.blesk.navigation.replaceWith
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** The screens of getting an account and setting it up, each one route below. */
internal fun EntryProviderScope<Any>.onboardingEntries(
    container: AppContainer,
    backStack: NavBackStack<NavKey>,
    direction: StackDirection,
) {
    // Only while [key] is on top: an answer coming after the person went back must not close the screen under it.
    fun close(key: NavKey) {
        if (backStack.lastOrNull() == key) backStack.removeAt(backStack.lastIndex)
    }
    entry<OtherServerKey> { key -> OtherServerRoute(container, key, onDone = { close(key) }, onUpdate = { backStack.add(UpdateKey) }) }
    entry<InviteKey> { key ->
        InviteRoute(container, key, onNext = { invite -> backStack.add(UsernameKey(key.server, invite)) }, onBack = { close(key) })
    }
    entry<UsernameKey> { key ->
        UsernameRoute(
            container = container,
            key = key,
            onInviteRefused = { backStack.openSignUp(key.server) },
            onOpen = { backStack.add(it) },
            onBack = { close(key) },
        )
    }
    entry<SetupKey> { key ->
        SetupRoute(
            container = container,
            key = key,
            onNext = {
                val next = key.step.next(pinSet = container.pinVault.isSet)
                container.scope.launch { container.onboarding.setSetupStep(next) }
                if (next != null) backStack.add(SetupKey(next))
            },
            onFinish = {
                container.scope.launch { container.finishSetup() }
                backStack.replaceWith(listOf(HomeKey), direction)
            },
        )
    }
}

/**
 * Opens the setup the moment the account it is for is saved with its first step, whichever screen
 * asked for the account and whether it is still there; and at a start while a step is still pending.
 * Only then: within the setup the screens lead the saved step, not the other way round.
 */
@Composable
internal fun SetupOpener(start: Flow<StartState>, backStack: NavBackStack<NavKey>, direction: StackDirection) {
    val haptics = rememberHaptics()
    LaunchedEffect(start) {
        var before: StartState? = null
        start.collect { now ->
            val step = (now as? StartState.Account)?.pendingSetup
            val entered = before !is StartState.Account
            before = now
            if (step == null || !entered || backStack.any { it is SetupKey }) return@collect
            // Made just now on this screen, rather than taken up again at a start.
            if (backStack.any { it is UsernameKey }) haptics.success()
            backStack.replaceWith(listOf(SetupKey(step)), direction)
        }
    }
}

/**
 * «Создать аккаунт» before there is an account: a first look at the chosen server, then its first
 * step. Taking another way stops the look, so a late answer never opens sign-up over that way.
 */
@Stable
internal class SignUpStart(private val signUp: SignUp, private val scope: CoroutineScope, private val open: (NavKey) -> Unit) {
    var busy by mutableStateOf(false)
        private set
    var problem by mutableStateOf<AccessProblem?>(null)
        private set
    private var looking: Job? = null

    fun begin() {
        if (busy) return
        busy = true
        problem = null
        val address = signUp.server
        looking = scope.launch {
            when (val probe = signUp.probe(address)) {
                is Probe.Ready -> open(firstSignUpKey(ServerRef.of(address), probe.info))
                is Probe.Failed -> problem = probe.problem
            }
            busy = false
        }
    }

    fun stop() {
        looking?.cancel()
        busy = false
    }
}

@Composable
internal fun rememberSignUpStart(container: AppContainer, open: (NavKey) -> Unit): SignUpStart {
    val scope = rememberCoroutineScope()
    return remember(container, scope) { SignUpStart(container.signUp, scope, open) }
}

/**
 * «Другой сервер»: the typed or scanned address is looked at before it is taken, so a wrong one is
 * said here and not at the end of the sign-up.
 */
@Composable
private fun OtherServerRoute(container: AppContainer, key: OtherServerKey, onDone: () -> Unit, onUpdate: () -> Unit) {
    val signUp = container.signUp
    val scope = rememberCoroutineScope()
    val address = rememberTextFieldState(key.scanned?.address()?.link().orEmpty())
    val last by container.onboarding.lastServer.collectAsStateWithLifecycle(initialValue = null)
    val proxy by container.settings.proxy.collectAsStateWithLifecycle(initialValue = ProxySetting())
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<AccessProblem?>(null) }
    var looking by remember { mutableStateOf<Job?>(null) }
    fun leave() {
        looking?.cancel()
        onDone()
    }
    fun check() {
        if (busy) return
        val parsed = ServerAddress.parse(address.text.toString())
        if (parsed == null) {
            problem = AccessProblem.BadAddress
            return
        }
        busy = true
        problem = null
        looking = scope.launch {
            when (val probe = signUp.probe(parsed)) {
                is Probe.Ready -> {
                    signUp.server = parsed
                    if (parsed != ServerAddress.DEFAULT) container.onboarding.rememberServer(parsed.url, parsed.pin)
                    onDone()
                }
                is Probe.Failed -> problem = probe.problem
            }
            busy = false
        }
    }
    ServerStepScreen(
        address = address,
        state = ServerStepState(
            last = last?.let { KnownServer(it.url.removePrefix("https://"), ServerAddress(it.url, it.pin).link()) },
            custom = signUp.server != ServerAddress.DEFAULT,
            busy = busy,
            problem = problem,
        ),
        actions = ServerStepActions(
            onDone = ::check,
            onBack = ::leave,
            onBlesk = {
                signUp.server = ServerAddress.DEFAULT
                leave()
            },
            onUpdate = onUpdate.takeIf { problem == AccessProblem.AppTooOld },
        ),
        scanner = { onCode -> QrScanner(onCode = onCode, modifier = Modifier.fillMaxSize()) },
        proxy = {
            ProxySection(
                proxy = proxy,
                onSave = { scope.launch { container.settings.setProxy(it) } },
                onCheck = { checkProxy(container, it) },
                heading = false,
            )
        },
    )
}

/** «Инвайт-код»: filled in from an invite link that came before there was an account. */
@Composable
private fun InviteRoute(container: AppContainer, key: InviteKey, onNext: (String?) -> Unit, onBack: () -> Unit) {
    val signUp = container.signUp
    val code = rememberTextFieldState()
    val pending by container.pendingInvite.collectAsStateWithLifecycle()
    LaunchedEffect(pending) {
        val invite = pending ?: return@LaunchedEffect
        code.setTextAndPlaceCursorAtEnd(invite)
        // Taken once: coming back to this step keeps what was typed over it since.
        container.pendingInvite.value = null
    }
    val info = signUp.info(key.server.address())
    val typed = code.text.toString()
    InviteStepScreen(
        code = code,
        state = InviteStepState(
            required = inviteRequired(info),
            problem = signUp.refusedInvite?.takeIf { it.code == typed }?.problem,
        ),
        actions = InviteStepActions(onNext = onNext, onBack = onBack),
    )
}

/**
 * «Как тебя найдут» and «Создать аккаунт». A name the server turns down is told under the field,
 * as the live check would have; a turned-down invite sends the person back to its step.
 */
@Composable
private fun UsernameRoute(
    container: AppContainer,
    key: UsernameKey,
    onInviteRefused: () -> Unit,
    onOpen: (NavKey) -> Unit,
    onBack: () -> Unit,
) {
    val signUp = container.signUp
    val address = key.server.address()
    val scope = rememberCoroutineScope()
    val input = rememberUsernameInput()
    val checks = rememberUsernameChecks(input) { signUp.checkName(address, it) }
    var busy by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<AccessProblem?>(null) }
    fun refuseInvite(why: AccessProblem) {
        signUp.refusedInvite = RefusedInvite(key.invite.orEmpty(), why)
        onInviteRefused()
    }
    fun create() {
        val name = input.text
        busy = true
        problem = null
        scope.launch {
            val result = signUp.create(address, key.invite, name)
            // The account's setup opens from its saved state; this screen only stays busy until it goes.
            if (result == RegisterResult.Ok) return@launch
            busy = false
            when (result) {
                RegisterResult.Ok -> Unit
                RegisterResult.NameTaken -> checks.learn(name, UsernameCheck(false, UsernameVerdict.TAKEN, name))
                RegisterResult.NameInvalid -> checks.learn(name, UsernameCheck(false, UsernameVerdict.INVALID, name))
                RegisterResult.InviteRequired -> refuseInvite(AccessProblem.InviteRequired)
                RegisterResult.InviteInvalid -> refuseInvite(AccessProblem.InviteInvalid)
                is ServerTrouble -> problem = result.problem(address)
            }
        }
    }
    UsernameStepScreen(
        input = input,
        status = checks.status,
        state = UsernameStepState(busy = busy, problem = problem),
        actions = UsernameStepActions(
            onCreate = ::create,
            onBack = onBack,
            onTerms = { onOpen(TermsKey) },
            onPolicy = { onOpen(PolicyKey) },
            onUpdate = { onOpen(UpdateKey) }.takeIf { problem == AccessProblem.AppTooOld },
        ),
    )
}
