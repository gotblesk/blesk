package app.blesk.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.blesk.feature.settings.SettingsList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import java.time.Instant

enum class HomeTab { Chats, Contacts, Settings }

/** The three tabs under the two lenses. */
@Composable
internal fun Home(
    container: AppContainer,
    tab: HomeTab,
    demo: Boolean,
    debugUnlocked: Boolean,
    onSelectTab: (HomeTab) -> Unit,
    onOpenConversation: (String) -> Unit,
    onOpenChannel: (Conversation, RoomChannel) -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenDebug: () -> Unit,
) {
    val tabs = persistentListOf(
        LensTab(stringResource(R.string.tab_chats), BleskIcons.Chats),
        LensTab(stringResource(R.string.tab_contacts), BleskIcons.People),
        LensTab(stringResource(R.string.tab_settings), BleskIcons.Settings),
    )
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
                val conversations by container.demo.conversations.collectAsStateWithLifecycle(initialValue = emptyList())
                val typing by container.demo.typing.collectAsStateWithLifecycle()
                ChatList(
                    conversations = conversations.toImmutableList(),
                    typing = typing.toImmutableSet(),
                    now = remember(conversations) { Instant.now() },
                    contentPadding = padding,
                    onOpenConversation = onOpenConversation,
                    onOpenChannel = onOpenChannel,
                )
            }
            HomeTab.Contacts -> ContactList(
                contacts = container.demo.contacts.toImmutableList(),
                contentPadding = padding,
                onOpenContact = { contact -> onOpenConversation(container.demo.directChatWith(contact.id)) },
            )
            HomeTab.Settings -> SettingsList(
                demo = demo,
                debugUnlocked = debugUnlocked,
                contentPadding = padding,
                onOpenAppearance = onOpenAppearance,
                onOpenAbout = onOpenAbout,
                onOpenDebug = onOpenDebug,
            )
        }
    }
}
