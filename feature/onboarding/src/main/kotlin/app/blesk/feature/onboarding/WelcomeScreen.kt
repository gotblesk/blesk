package app.blesk.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.ChatBackdrop
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/**
 * What the welcome screen shows: the server sign-up goes to when it is not blesk's own, whether
 * creating an account is under way, and why it stopped.
 */
@Immutable
data class WelcomeState(val server: String? = null, val busy: Boolean = false, val problem: AccessProblem? = null)

/**
 * The welcome screen's choices. Without [onHaveAccount] signing in is not ready yet: its capsule is
 * marked «скоро» and only says when it comes. [onUpdate] opens the app's update under «Приложение
 * устарело».
 */
@Stable
class WelcomeActions(
    val onCreate: () -> Unit,
    val onHaveAccount: (() -> Unit)?,
    val onDemo: () -> Unit,
    val onOtherServer: () -> Unit,
    val onUpdate: (() -> Unit)? = null,
)

/**
 * The first screen: the name, one line about what blesk is, and the three ways in stuck together
 * into one drop. Only type, light and the drop, no pictures. A server other than blesk's own is one
 * small link away.
 */
@Composable
fun WelcomeScreen(state: WelcomeState, actions: WelcomeActions, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Blesk.colors.background)) {
        ChatBackdrop(HeaderStyle.Light(1), photo = null)
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    // At least the screen's height, so the weights below centre the content; with large type it scrolls.
                    .heightIn(min = maxHeight)
                    .windowInsetsPadding(WindowInsets.systemBars)
                    .padding(horizontal = Blesk.dimens.gutter),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.onboarding_wordmark),
                    style = Blesk.type.wordmark,
                    modifier = Modifier.semantics { heading() },
                )
                Text(
                    text = stringResource(R.string.onboarding_welcome_line),
                    color = Blesk.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp),
                )
                Spacer(Modifier.height(32.dp))
                WelcomeCluster(state.busy, state.problem, actions)
                Spacer(Modifier.height(16.dp))
                ServerLink(state.server, actions.onOtherServer)
                // Below the server link and with room kept for it, so the ways in stay put when a line shows.
                StatusLine(
                    busy = state.busy,
                    busyText = stringResource(R.string.onboarding_connecting),
                    problem = state.problem,
                    modifier = Modifier.heightIn(min = StatusRoom),
                    centered = true,
                    onUpdate = actions.onUpdate,
                )
                Spacer(Modifier.weight(BELOW_SHARE))
            }
        }
    }
}

/** «Другой сервер», or the one chosen with «Сменить»: either way a way to the address step. */
@Composable
private fun ServerLink(server: String?, onClick: () -> Unit) {
    if (server == null) {
        LinkRow(stringResource(R.string.onboarding_other_server), BleskIcons.Server, onClick)
    } else {
        LinkRow(
            text = stringResource(R.string.onboarding_server, server),
            icon = BleskIcons.Server,
            onClick = onClick,
            action = stringResource(R.string.onboarding_server_change),
        )
    }
}

/** Two lines of a status, kept free under the server link. */
private val StatusRoom = 56.dp

/** The room under the content against the room above it: the content sits a little above the middle. */
private const val BELOW_SHARE = 1.6f
