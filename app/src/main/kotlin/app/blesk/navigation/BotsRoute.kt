package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.data.BotAccounts
import app.blesk.feature.settings.BotActions
import app.blesk.feature.settings.BotsScreen
import app.blesk.shared.protocol.BotInfo
import kotlinx.collections.immutable.toImmutableList

@Composable
internal fun BotsRoute(container: AppContainer, onBack: () -> Unit) {
    val bots = container.chats.collectAsStateWithLifecycle().value as? BotAccounts ?: return
    var version by remember { mutableIntStateOf(0) }
    val mine by produceState<List<BotInfo>?>(null, version) { value = bots.myBots() }
    BotsScreen(
        bots = mine?.toImmutableList(),
        actions = BotActions(
            create = { username, name, about -> bots.createBot(username, name, about).also { version++ } },
            renew = { username -> bots.renewToken(username) },
            delete = { username ->
                bots.deleteBot(username)
                version++
            },
        ),
        onBack = onBack,
    )
}
