package app.blesk.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import app.blesk.core.data.BotResult
import app.blesk.core.data.BotWithToken
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.NotEncryptedTag
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.SkeletonRows
import app.blesk.core.design.component.StateMessage
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.rememberCopy
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.shared.protocol.BotInfo
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import app.blesk.core.design.R as DesignR

/** What the bots screen can do; each action refreshes the list afterwards. */
class BotActions(
    val create: suspend (username: String, name: String, about: String) -> Pair<BotResult, BotWithToken?>,
    val renew: suspend (String) -> BotWithToken?,
    val delete: suspend (String) -> Unit,
)

@Composable
fun BotsScreen(
    /** Null while the list is on its way from the server. */
    bots: ImmutableList<BotInfo>?,
    actions: BotActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
) {
    val scope = rememberCoroutineScope()
    var shown by remember { mutableStateOf<BotWithToken?>(null) }
    var result by remember { mutableStateOf<BotResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)
    ScrollScreen(title = stringResource(R.string.bots_title), onBack = onBack, modifier = modifier) {
        Row(pad.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) { NotEncryptedTag() }
        Text(stringResource(R.string.bots_note), style = Blesk.type.label, color = Blesk.colors.textSecondary, modifier = pad)

        shown?.let { token -> TokenCard(token, onDone = { shown = null }) }

        SectionHeader(stringResource(R.string.bots_mine))
        if (failed) {
            StateMessage(
                text = stringResource(DesignR.string.design_failed),
                action = stringResource(DesignR.string.design_retry),
                onAction = onRetry,
            )
        } else if (bots == null) {
            SkeletonRows(count = 2, avatar = 40.dp)
        } else if (bots.isEmpty()) {
            Text(stringResource(R.string.bots_none), color = Blesk.colors.textSecondary, modifier = pad.padding(horizontal = 4.dp))
        } else {
            Card(pad) {
                bots.forEach { bot ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter, vertical = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Avatar(bot.name, size = 40.dp)
                            Column(Modifier.weight(1f)) {
                                Text(bot.name, style = Blesk.type.bodyStrong, maxLines = 1)
                                Text("@${bot.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                            }
                        }
                        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                text = stringResource(R.string.bots_renew),
                                kind = ButtonKind.Secondary,
                                onClick = { scope.launch { shown = actions.renew(bot.username) } },
                            )
                            Button(
                                text = stringResource(R.string.bots_delete),
                                icon = BleskIcons.Trash,
                                kind = ButtonKind.Secondary,
                                onClick = { scope.launch { actions.delete(bot.username) } },
                            )
                        }
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.bots_new))
        val username = rememberTextFieldState()
        val name = rememberTextFieldState()
        val about = rememberTextFieldState()
        TextInput(
            label = stringResource(R.string.bots_username),
            state = username,
            hint = stringResource(R.string.bots_username_hint),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        )
        TextInput(label = stringResource(R.string.bots_name), state = name)
        TextInput(label = stringResource(R.string.bots_about), state = about, singleLine = false)
        val error = when (result) {
            BotResult.NameTaken -> R.string.bots_taken
            BotResult.NameInvalid -> R.string.bots_invalid
            BotResult.TooMany -> R.string.bots_too_many
            BotResult.Failed -> R.string.bots_failed
            BotResult.Ok, null -> null
        }
        error?.let {
            Text(
                text = stringResource(it),
                color = Blesk.colors.danger,
                modifier = pad.padding(horizontal = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        Button(
            text = stringResource(R.string.bots_create),
            onClick = {
                val title = name.text.toString().trim()
                if (busy || title.isEmpty() || username.text.isBlank()) return@Button
                busy = true
                scope.launch {
                    val (outcome, created) = actions.create(username.text.toString(), title, about.text.toString())
                    result = outcome
                    if (created != null) {
                        shown = created
                        username.clearText()
                        name.clearText()
                        about.clearText()
                    }
                    busy = false
                }
            },
            modifier = Modifier.padding(Blesk.dimens.gutter).fillMaxWidth(),
        )
    }
}

@Composable
private fun TokenCard(token: BotWithToken, onDone: () -> Unit) {
    val copy = rememberCopy()
    var copied by remember(token) { mutableStateOf(false) }
    Card(Modifier.padding(Blesk.dimens.gutter)) {
        Column(Modifier.padding(Blesk.dimens.gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.bots_token_title, token.bot.username), style = Blesk.type.bodyStrong)
            Text(stringResource(R.string.bots_token_note), style = Blesk.type.label, color = Blesk.colors.textSecondary)
            Text(token.token, style = Blesk.type.caption)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    text = stringResource(if (copied) R.string.bots_copied else R.string.bots_copy),
                    icon = BleskIcons.Copy,
                    onClick = {
                        copy(token.token, true)
                        copied = true
                    },
                )
                Button(text = stringResource(R.string.bots_done), kind = ButtonKind.Secondary, onClick = onDone)
            }
        }
    }
}
