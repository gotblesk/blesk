package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.core.messaging.InviteBook
import app.blesk.core.messaging.NewInvite
import app.blesk.feature.settings.InviteCreation
import app.blesk.feature.settings.InvitesScreen
import app.blesk.shared.protocol.InviteList

/** «Ты → Приглашения» on blesk's own server, the one every invite link leads to; nothing else opens it. */
@Composable
internal fun InvitesRoute(container: AppContainer, onBack: () -> Unit) {
    container.chats.collectAsStateWithLifecycle().value ?: return
    val api = container.api ?: return
    val book = remember(api) { InviteBook(api) }
    var version by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    // The list on screen stays while a newer one loads, so making an invite does not blink it away.
    val invites by produceState<InviteList?>(null, version) {
        failed = false
        val loaded = book.list()
        failed = loaded == null && value == null
        if (loaded != null) value = loaded
    }
    InvitesScreen(
        invites = invites,
        failed = failed,
        onRetry = { version++ },
        onCreate = {
            when (val made = book.create()) {
                is NewInvite.Made -> InviteCreation.Made(made.invite.code).also { version++ }
                NewInvite.Limit -> InviteCreation.Limit.also { version++ }
                NewInvite.TooOften -> InviteCreation.TooOften
                NewInvite.Failed -> InviteCreation.Failed
            }
        },
        onBack = onBack,
    )
}
