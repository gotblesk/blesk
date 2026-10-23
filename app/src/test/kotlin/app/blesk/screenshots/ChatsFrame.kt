package app.blesk.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.blesk.core.data.Connection
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.glass.LensAction
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.glass.LensTab
import app.blesk.core.design.glass.LensTabBar
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.icon.LivingIcon
import app.blesk.feature.chats.ConnectionStatus
import kotlinx.collections.immutable.persistentListOf

/** «Чаты» between the lenses, as the app draws the tab, around [content]. */
@Composable
internal fun ChatsFrame(content: @Composable (PaddingValues) -> Unit) {
    val tabs = persistentListOf(
        LensTab("Чаты", BleskIcons.Chats, LivingIcon.Fill),
        LensTab("Контакты", BleskIcons.People, LivingIcon.Gather),
        LensTab("Комнаты", BleskIcons.Rooms, LivingIcon.Stick),
        LensTab("Настройки", BleskIcons.Settings, LivingIcon.Slide),
        LensTab("Ты", BleskIcons.Person) { Avatar("Ева", size = 24.dp) },
    )
    LensScaffold(
        top = {
            Column {
                TopLens(title = "Чаты", collapsed = false, actions = { IconButton(BleskIcons.Search, "Поиск", {}) })
                ConnectionStatus(Connection.Online, onRetry = {}, showAfterMillis = 0)
            }
        },
        bottom = {
            LensTabBar(tabs = tabs, selected = 0, onSelect = {}, action = LensAction(BleskIcons.NewChat, "Новый чат", {}))
        },
        content = content,
    )
}
