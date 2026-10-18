package app.blesk.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.R
import app.blesk.core.data.settings.AppMode
import app.blesk.core.design.component.Choice
import app.blesk.core.design.component.ChoiceSheet
import app.blesk.core.design.component.rememberNotify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Follows the links other apps hand over in [AppContainer.linkRequest], once the chats are open
 * and never behind the app lock. Any app can send a link without the person tapping anything, so
 * a link that would show the person to others, a join request to a group's admin or joining a
 * public space, is asked about first. Scanned and pasted links are the person's own choice and
 * act at once on their screens.
 *
 * [atHome] tells when the chats are on screen again: a link that needed an account opens then,
 * after the person made one.
 */
@Composable
internal fun IncomingLinks(container: AppContainer, screens: LinkScreens, atHome: Boolean) {
    val chats = container.chats.collectAsStateWithLifecycle().value
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = null)
    val locked = container.lock.locked
    val context = LocalContext.current
    val notify = rememberNotify()
    val scope = rememberCoroutineScope()
    var asking by remember { mutableStateOf<LinkTarget?>(null) }
    var afterSignUp by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(chats, locked) {
        if (chats == null || locked) return@LaunchedEffect
        deliverLinks(container.linkRequest, locked = { container.lock.locked }, scope) { link ->
            // The mode as stored, not the collected one, which is null until settings load.
            val demo = container.settings.mode.first() is AppMode.Demo
            val target = LinkTarget.of(link)
            if (target != null && !demo && target.revealsPerson) {
                asking = target
                return@deliverLinks
            }
            val outcome = target?.let { follow(it, chats, demo, container.pendingInvite, screens) } ?: LinkOutcome.NotFound
            if (outcome == LinkOutcome.NeedsAccount) {
                afterSignUp = link
                notify(context.getString(R.string.link_after_account))
            } else {
                outcome.notice?.let { notify(context.getString(it)) }
            }
        }
    }

    LaunchedEffect(afterSignUp, atHome, mode) {
        val link = afterSignUp ?: return@LaunchedEffect
        if (atHome && mode is AppMode.Server) {
            afterSignUp = null
            container.linkRequest.value = link
        }
    }

    asking?.let { target ->
        val question = target.question() ?: return@let
        ChoiceSheet(
            title = question.title,
            text = question.text,
            choices = listOf(
                Choice(question.action) {
                    scope.launch {
                        val repository = container.chats.value ?: return@launch
                        reach(target, repository, screens.conversation, screens.profile).notice?.let { notify(context.getString(it)) }
                    }
                },
            ),
            onDismiss = { asking = null },
        )
    }
}

/**
 * Hands each link waiting in [requests] to [open], launched in [scope]: whatever ends this
 * collection, a new repository or the lock coming on, must not cut a request in half. While
 * [locked], a link stays where it is; the lock may come on together with the link, before the
 * collection is restarted.
 */
internal suspend fun deliverLinks(
    requests: MutableStateFlow<String?>,
    locked: () -> Boolean,
    scope: CoroutineScope,
    open: suspend (String) -> Unit,
) {
    requests.filterNotNull().collect { link ->
        if (locked()) return@collect
        requests.value = null
        scope.launch { open(link) }
    }
}

/** A link that tells someone else who the person is as soon as it is followed. */
internal val LinkTarget.revealsPerson: Boolean
    get() = this is LinkTarget.Group || this is LinkTarget.Space

private class LinkQuestion(val title: String, val text: String, val action: String)

@Composable
private fun LinkTarget.question(): LinkQuestion? = when (this) {
    is LinkTarget.Group -> LinkQuestion(
        title = stringResource(R.string.link_ask_join_title, admin),
        text = stringResource(R.string.link_ask_join_text),
        action = stringResource(R.string.link_ask_join_action),
    )
    is LinkTarget.Space -> LinkQuestion(
        title = stringResource(R.string.link_ask_space_title, handle),
        text = stringResource(R.string.link_ask_space_text),
        action = stringResource(R.string.link_ask_space_action),
    )
    else -> null
}
