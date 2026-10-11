package app.blesk.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.model.Conversation
import app.blesk.core.data.model.RoomChannel
import app.blesk.core.design.component.DemoTag
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.feature.chats.ChatList
import app.blesk.feature.chats.ContactList
import app.blesk.feature.chats.EmptyChats
import app.blesk.feature.settings.SettingsActions
import app.blesk.feature.settings.SettingsList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.launch
import java.time.Instant

enum class HomeTab { Chats, Contacts, Settings }

/** The three tabs under the two lenses. */
@Composable
internal fun Home(
    container: AppContainer,
    tab: HomeTab,
    demo: Boolean,
    serverHost: String?,
    debugUnlocked: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenChannel: (Conversation, RoomChannel) -> Unit,
    settings: SettingsActions,
) {
    val tabs = persistentListOf(
        LensTab(stringResource(R.string.tab_chats), BleskIcons.Chats),
        LensTab(stringResource(R.string.tab_contacts), BleskIcons.People),
        LensTab(stringResource(R.string.tab_settings), BleskIcons.Settings),
    )
    val chats = container.chats.collectAsStateWithLifecycle().value ?: return
    val scope = rememberCoroutineScope()
    LensScaffold(
        top = {
            TopLens(
                title = tabs[tab.ordinal].label,
                actions = { if (demo) DemoTag(Modifier.padding(end = 10.dp)) },
            )
        },
        bottom = { LensTabBar(tabs = tabs, selected = tab.ordinal, onSelect = { onSelectTab(HomeTab.entries[it]) }) },
    ) { padding ->
        when (tab) {
            HomeTab.Chats -> {
                val conversations by chats.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
                val typing by chats.typing.collectAsStateWithLifecycle()
                if (conversations.isEmpty() && !demo) {
                    EmptyChats(padding)
                    return@LensScaffold
                }
                ChatList(
                    conversations = conversations.toImmutableList(),
                    typing = typing.toImmutableSet(),
                    now = remember(conversations) { Instant.now() },
                    contentPadding = padding,
                    onOpenConversation = onOpenConversation,
                    onOpenChannel = onOpenChannel,
                )
            }
            HomeTab.Contacts -> {
                val contacts by chats.contacts.collectAsStateWithLifecycle(initialValue = emptyList())
                ContactList(
                    contacts = contacts.toImmutableList(),
                    contentPadding = padding,
                    onOpenContact = { contact ->
                        scope.launch { chats.openDirect(contact.username)?.let(onOpenConversation) }
                    },
                    onFind = if (demo) null else { username -> chats.openDirect(username)?.also(onOpenConversation) != null },
                )
            }
            HomeTab.Settings -> {
                val profile by container.settings.profile.collectAsStateWithLifecycle(initialValue = null)
                val current = profile ?: return@LensScaffold
                SettingsList(
                    serverHost = serverHost,
                    debugUnlocked = debugUnlocked,
                    profile = current,
                    photo = rememberProfilePhoto(container, current.photoVersion),
                    contentPadding = padding,
                    actions = settings,
                )
            }
        }
    }
}
