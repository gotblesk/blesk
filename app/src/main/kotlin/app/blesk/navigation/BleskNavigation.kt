package app.blesk.navigation

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.core.data.diagnostics.EventCode
import app.blesk.core.data.settings.AppMode
import app.blesk.feature.chats.ChatListScreen
import app.blesk.feature.chats.ConversationScreen
import app.blesk.feature.debug.DebugScreen
import app.blesk.feature.settings.AboutScreen
import app.blesk.feature.settings.AppearanceScreen
import app.blesk.feature.settings.SettingsScreen
import app.blesk.shared.appearance.Appearance
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable data object ChatsKey : NavKey

@Serializable data class ConversationKey(val id: String) : NavKey

@Serializable data object SettingsKey : NavKey

@Serializable data object AppearanceKey : NavKey

@Serializable data object AboutKey : NavKey

@Serializable data object DebugKey : NavKey

@Composable
fun BleskNavigation(container: AppContainer) {
    val backStack = rememberNavBackStack(ChatsKey)
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
            entry<ChatsKey> {
                val conversations by container.demo.conversations.collectAsStateWithLifecycle()
                ChatListScreen(
                    conversations = conversations.toImmutableList(),
                    demo = demo,
                    now = remember(conversations) { Instant.now() },
                    onOpenConversation = { backStack.add(ConversationKey(it)) },
                    onOpenSettings = { backStack.add(SettingsKey) },
                )
            }
            entry<ConversationKey> { key ->
                val conversation by container.demo.conversation(key.id).collectAsStateWithLifecycle(initialValue = null)
                val messages by container.demo.messages(key.id).collectAsStateWithLifecycle(initialValue = emptyList())
                LaunchedEffect(key.id) { container.demo.markRead(key.id) }
                conversation?.let {
                    ConversationScreen(
                        conversation = it,
                        messages = messages.toImmutableList(),
                        authorName = { id -> container.demo.contact(id)?.name },
                        onSend = { text -> container.demo.send(key.id, text) },
                        onBack = back,
                    )
                }
            }
            entry<SettingsKey> {
                SettingsScreen(
                    demo = demo,
                    debugUnlocked = debugUnlocked,
                    onBack = back,
                    onOpenAppearance = { backStack.add(AppearanceKey) },
                    onOpenAbout = { backStack.add(AboutKey) },
                    onOpenDebug = { backStack.add(DebugKey) },
                )
            }
            entry<AppearanceKey> {
                AppearanceScreen(
                    current = appearance.theme,
                    onSelectTheme = { spec ->
                        scope.launch { container.settings.updateAppearance { it.copy(theme = spec) } }
                        container.eventLog.record(EventCode.ThemeChanged, spec.preset.ordinal.toLong())
                    },
                    onBack = back,
                )
            }
            entry<AboutKey> {
                AboutScreen(
                    versionName = BuildConfig.VERSION_NAME,
                    debugUnlocked = debugUnlocked,
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
