package app.blesk.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.model.RoomChannelKind
import app.blesk.core.data.settings.AppMode
import app.blesk.feature.chats.VoiceChannelScreen
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.security.WipeScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.License
import app.blesk.feature.settings.LicenseTextScreen
import app.blesk.feature.settings.LicensesScreen
import app.blesk.feature.settings.SettingsActions
import app.blesk.shared.appearance.Appearance
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object HomeKey : NavKey

@Serializable
data class ConversationKey(val id: String) : NavKey

@Serializable
data class VoiceChannelKey(val roomId: String, val channelId: String) : NavKey

@Serializable
data object AppearanceKey : NavKey

@Serializable
data object AboutKey : NavKey

@Serializable
data object DebugKey : NavKey

@Serializable
data object ProfileKey : NavKey

@Serializable
data object PrivacyKey : NavKey

@Serializable
data class PinChangeKey(val requireCurrent: Boolean) : NavKey

@Serializable
data object PinDisableKey : NavKey

@Serializable
data object WipeKey : NavKey

@Serializable
data object LicensesKey : NavKey

@Serializable
data class LicenseKey(val license: License) : NavKey

@Composable
fun BleskNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(HomeKey)
    val scope = rememberCoroutineScope()
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val debugUnlocked by container.settings.debugUnlocked.collectAsStateWithLifecycle(initialValue = false)
    val appearance by container.settings.appearance.collectAsStateWithLifecycle(initialValue = Appearance())
    val demo = mode is AppMode.Demo
    val back: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    LaunchedEffect(backStack.size) {
        container.eventLog.record(EventCode.ScreenOpened, backStack.size.toLong())
    }

    NavDisplay(
        backStack = backStack,
        onBack = back,
        entryProvider = entryProvider {
            entry<HomeKey> {
                var tab by rememberSaveable { mutableStateOf(HomeTab.Chats) }
                Home(
                    container = container,
                    tab = tab,
                    demo = demo,
                    debugUnlocked = debugUnlocked,
                    onSelectTab = { tab = it },
                    onOpenConversation = { backStack.add(ConversationKey(it)) },
                    onOpenChannel = { room, channel ->
                        val target = if (channel.kind == RoomChannelKind.Voice) {
                            VoiceChannelKey(room.id, channel.id)
                        } else {
                            ConversationKey(channel.id)
                        }
                        backStack.add(target)
                    },
                    settings = SettingsActions(
                        onOpenProfile = { backStack.add(ProfileKey) },
                        onOpenAppearance = { backStack.add(AppearanceKey) },
                        onOpenPrivacy = { backStack.add(PrivacyKey) },
                        onOpenAbout = { backStack.add(AboutKey) },
                        onOpenDebug = { backStack.add(DebugKey) },
                    ),
                )
            }
            entry<ProfileKey> { ProfileRoute(container, back) }
            entry<PrivacyKey> {
                PrivacyRoute(
                    container = container,
                    onSetPin = { backStack.add(PinChangeKey(requireCurrent = false)) },
                    onChangePin = { backStack.add(PinChangeKey(requireCurrent = true)) },
                    onDisablePin = { backStack.add(PinDisableKey) },
                    onWipe = { backStack.add(WipeKey) },
                    onBack = back,
                )
            }
            entry<PinChangeKey> { key -> PinChangeRoute(container, key.requireCurrent, back) }
            entry<PinDisableKey> { PinDisableRoute(container, back) }
            entry<WipeKey> {
                val context = LocalContext.current
                WipeScreen(onWipe = { wipeEverything(context, container) }, onBack = back)
            }
            entry<LicensesKey> { LicensesScreen(onOpenLicense = { backStack.add(LicenseKey(it)) }, onBack = back) }
            entry<LicenseKey> { key -> LicenseTextScreen(key.license, onBack = back) }
            entry<ConversationKey> { key ->
                ConversationRoute(container, key.id, appearance, back)
            }
            entry<VoiceChannelKey> { key ->
                val channel by container.demo.conversation(key.roomId)
                    .map { room -> room?.channels?.firstOrNull { it.id == key.channelId } }
                    .collectAsStateWithLifecycle(initialValue = null)
                channel?.let { VoiceChannelScreen(channel = it, onBack = back) }
            }
            entry<AppearanceKey> {
                AppearanceRoute(container, appearance, back)
            }
            entry<AboutKey> {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    debugUnlocked = debugUnlocked,
                    onOpenLicenses = { backStack.add(LicensesKey) },
                    onUnlockDebug = {
                        scope.launch { container.settings.setDebugUnlocked(true) }
                        container.eventLog.record(EventCode.DebugUnlocked)
                    },
                    onBack = back,
                )
            }
            entry<DebugKey> {
                val entries by container.eventLog.entries.collectAsStateWithLifecycle()
                var crashVersion by remember { mutableIntStateOf(0) }
                val crash = remember(crashVersion) { container.crashStore.read() }
                DebugScreen(
                    entries = entries.toImmutableList(),
                    crash = crash,
                    appInfo = "blesk ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}), Android ${Build.VERSION.SDK_INT}",
                    onClearCrash = {
                        container.crashStore.clear()
                        container.eventLog.record(EventCode.CrashCleared)
                        crashVersion++
                    },
                    onClearJournal = container.eventLog::clear,
                    onBack = back,
                )
            }
        },
    )
}
