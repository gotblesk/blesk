package app.blesk.feature.chats

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.core.data.model.Contact
import app.blesk.core.data.model.MAX_MESSAGE_CHARS
import app.blesk.core.data.model.Message
import app.blesk.core.data.model.splitLongText
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.IconButton
import app.blesk.core.design.component.Text
import app.blesk.core.design.glass.BottomLens
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.feature.chats.media.LockDrop
import app.blesk.feature.chats.media.RecordButton
import app.blesk.feature.chats.media.Recorders
import app.blesk.feature.chats.media.RecordingBar
import app.blesk.feature.chats.media.RecordingUi
import app.blesk.shared.protocol.BotCommand
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

@Composable
internal fun Composer(
    state: TextFieldState,
    recording: RecordingUi,
    recorders: Recorders?,
    busy: Boolean,
    onPickMedia: () -> Unit,
    onPickFile: () -> Unit,
    attachments: Boolean,
    onTakePhoto: (() -> Unit)? = null,
    onPickMusic: (() -> Unit)? = null,
    commands: ImmutableList<BotCommand>,
    members: ImmutableList<Contact>,
    online: Boolean,
    sendByEnter: Boolean,
    recentEmoji: ImmutableList<String>,
    onEmojiPicked: (String) -> Unit,
    replyTo: Message?,
    editing: Message?,
    authorName: (Message) -> String,
    onCancel: () -> Unit,
    onSend: (String) -> Unit,
) {
    val hint = stringResource(R.string.chat_input_hint)
    var attachMenu by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    BackHandler(enabled = emojiOpen) { emojiOpen = false }
    fun send() {
        val text = state.text.toString()
        if (text.isNotBlank()) {
            onSend(text)
            state.clearText()
        }
    }
    Box {
        BottomLens {
            AnimatedVisibility(visible = replyTo != null || editing != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = when {
                            editing != null -> stringResource(R.string.chat_editing)
                            replyTo != null -> stringResource(R.string.chat_replying_to, "${authorName(replyTo)}: ${replyTo.text}")
                            else -> ""
                        },
                        style = Blesk.type.label,
                        color = Blesk.colors.textSecondary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(BleskIcons.Close, stringResource(R.string.chat_cancel), onCancel)
                }
            }
            AnimatedVisibility(visible = attachMenu && !recording.active) {
                AttachMenu(
                    choices = listOfNotNull(
                        onTakePhoto?.let { AttachChoice(BleskIcons.Camera, stringResource(R.string.media_camera), it) },
                        AttachChoice(BleskIcons.Image, stringResource(R.string.media_photo_video), onPickMedia),
                        AttachChoice(BleskIcons.Document, stringResource(R.string.media_file), onPickFile),
                        onPickMusic?.let { AttachChoice(BleskIcons.Music, stringResource(R.string.media_music), it) },
                    ),
                    onChosen = { attachMenu = false },
                )
            }
            if (busy) {
                Text(
                    text = stringResource(R.string.media_preparing),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
            if (!online) {
                Text(
                    text = stringResource(R.string.chat_offline),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val typed = state.text.toString()
            if (typed.length > MAX_MESSAGE_CHARS) {
                val parts = splitLongText(typed).size
                Text(
                    text = pluralStringResource(R.plurals.chat_long_text, parts, parts),
                    style = Blesk.type.label,
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            val suggestions = if (typed.startsWith("/") && ' ' !in typed) {
                commands.filter { it.command.startsWith(typed.removePrefix("/")) }.take(MAX_SUGGESTIONS)
            } else {
                emptyList()
            }
            suggestions.forEach { command ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { state.setTextAndPlaceCursorAtEnd("/${command.command} ") }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("/${command.command}", style = Blesk.type.bodyStrong)
                    Text(command.description, style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                }
            }
            val word = typed.substringAfterLast(' ').substringAfterLast('\n')
            if (word.startsWith("@") && members.isNotEmpty()) {
                val needle = word.removePrefix("@").lowercase()
                members.filter { it.username.startsWith(needle) || it.name.lowercase().startsWith(needle) }
                    .take(MAX_SUGGESTIONS)
                    .forEach { person ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Button) {
                                    state.setTextAndPlaceCursorAtEnd(typed.dropLast(word.length) + "@${person.username} ")
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(person.name, size = 28.dp)
                            Text(person.name, style = Blesk.type.bodyStrong, maxLines = 1)
                            Text("@${person.username}", style = Blesk.type.label, color = Blesk.colors.textSecondary, maxLines = 1)
                        }
                    }
            }
            if (recording.active && recorders != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RecordingBar(recording, recorders, Modifier.weight(1f))
                    if (!recording.locked) RecordButton(recording, recorders)
                }
                return@BottomLens
            }
            Row(verticalAlignment = Alignment.Bottom) {
                if (attachments) AttachToggle(open = attachMenu) { attachMenu = !attachMenu }
                Box(Modifier.weight(1f).heightIn(min = 48.dp).padding(start = 4.dp), contentAlignment = Alignment.CenterStart) {
                    if (state.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary)
                    BasicTextField(
                        state = state,
                        textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                        cursorBrush = SolidColor(Blesk.colors.accent),
                        lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 6),
                        keyboardOptions = KeyboardOptions(imeAction = if (sendByEnter) ImeAction.Send else ImeAction.Default),
                        onKeyboardAction = if (sendByEnter) KeyboardActionHandler { send() } else null,
                        // The padding is inside the field, so the whole 48dp row takes the tap.
                        decorator = { inner ->
                            Box(
                                modifier = Modifier.heightIn(min = 48.dp).padding(vertical = 12.dp),
                                contentAlignment = Alignment.CenterStart,
                            ) { inner() }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) emojiOpen = false }
                            .onPreviewKeyEvent { event ->
                                val enter = event.key == Key.Enter || event.key == Key.NumPadEnter
                                if (!sendByEnter || !enter || event.isShiftPressed) return@onPreviewKeyEvent false
                                if (event.type == KeyEventType.KeyDown) send()
                                true
                            }
                            .semantics { contentDescription = hint },
                    )
                }
                IconButton(
                    icon = if (emojiOpen) BleskIcons.Edit else BleskIcons.Smile,
                    contentDescription = stringResource(if (emojiOpen) R.string.emoji_keyboard else R.string.emoji_open),
                    onClick = {
                        if (emojiOpen) {
                            emojiOpen = false
                        } else {
                            attachMenu = false
                            keyboard?.hide()
                            focus.clearFocus()
                            emojiOpen = true
                        }
                    },
                )
                val motion = Blesk.motion
                // «Отправить» and the record button flow into each other rather than swap.
                AnimatedContent(
                    targetState = state.text.isBlank() && editing == null && recorders != null,
                    transitionSpec = {
                        (scaleIn(motion.settle(), initialScale = 0.4f) + fadeIn(motion.press()))
                            .togetherWith(scaleOut(motion.press(), targetScale = 0.4f) + fadeOut(motion.press()))
                    },
                    label = "send",
                ) { record ->
                    if (record && recorders != null) {
                        RecordButton(recording, recorders)
                    } else {
                        IconButton(
                            icon = if (editing != null) BleskIcons.Check else BleskIcons.Send,
                            contentDescription = stringResource(R.string.chat_send),
                            onClick = ::send,
                        )
                    }
                }
            }
            EmojiRoom(open = emojiOpen) { height ->
                EmojiPanel(
                    recent = recentEmoji,
                    onPick = { emoji ->
                        state.edit { replace(selection.min, selection.max, emoji) }
                        onEmojiPicked(emoji)
                    },
                    height = height,
                )
            }
        }
        if (recording.active && !recording.locked) {
            LockDrop(recording, Modifier.align(Alignment.TopEnd).padding(end = 16.dp).offset(y = (-150).dp))
        }
    }
}

/** Saves what is typed a moment after typing stops, and once more when the chat closes. */
@Composable
internal fun DraftKeeper(state: TextFieldState, onDraft: (String) -> Unit) {
    val save by rememberUpdatedState(onDraft)
    LaunchedEffect(state) {
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collectLatest { text ->
                delay(DRAFT_PAUSE_MILLIS)
                save(text)
            }
    }
    DisposableEffect(state) { onDispose { save(state.text.toString()) } }
}

/**
 * Room for the emoji panel exactly as tall as the last keyboard. While the keyboard slides away
 * the room grows by as much as the keyboard shrinks, and the other way round, so the composer
 * and the messages never jump when switching between the two. After the panel closes for the
 * keyboard the room is held until the keyboard is all the way up.
 */
@Composable
private fun EmojiRoom(open: Boolean, panel: @Composable (Dp) -> Unit) {
    val density = LocalDensity.current
    val ime = WindowInsets.ime.getBottom(density)
    val navigation = WindowInsets.navigationBars.getBottom(density)
    var keyboard by rememberSaveable { mutableIntStateOf(0) }
    if (ime > keyboard) keyboard = ime
    val full = if (keyboard > 0) keyboard else with(density) { DefaultPanel.roundToPx() + navigation }
    var holding by remember { mutableStateOf(false) }
    var wasOpen by remember { mutableStateOf(open) }
    if (wasOpen && !open) holding = true
    wasOpen = open
    LaunchedEffect(holding) {
        if (!holding) return@LaunchedEffect
        delay(HOLD_MAX_MILLIS)
        holding = false
    }
    if (holding && ime >= full - with(density) { 8.dp.roundToPx() }) holding = false
    val room = if (open || holding) (full - maxOf(ime, navigation)).coerceAtLeast(0) else 0
    if (room == 0 && !open) return
    val panelHeight = with(density) { (full - navigation).toDp() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(with(density) { room.toDp() })
            .clipToBounds(),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (open) Box(Modifier.requiredHeight(panelHeight)) { panel(panelHeight) }
    }
}

private val DefaultPanel = 300.dp
private const val HOLD_MAX_MILLIS = 700L

/** Sends "typing" at most every few seconds while text changes, and "stopped" after a pause. */
@Composable
internal fun TypingSignal(state: TextFieldState, onTyping: (Boolean) -> Unit) {
    LaunchedEffect(state) {
        var lastSent = 0L
        var active = false
        snapshotFlow { state.text.toString() }
            .drop(1)
            .collectLatest { text ->
                val now = System.currentTimeMillis()
                if (text.isNotBlank() && (!active || now - lastSent > TYPING_REPEAT_MILLIS)) {
                    onTyping(true)
                    active = true
                    lastSent = now
                }
                delay(TYPING_PAUSE_MILLIS)
                if (active) {
                    onTyping(false)
                    active = false
                }
            }
    }
}

private const val TYPING_REPEAT_MILLIS = 4_000L
private const val TYPING_PAUSE_MILLIS = 5_000L

private const val MAX_SUGGESTIONS = 5
private const val DRAFT_PAUSE_MILLIS = 800L

private class AttachChoice(val icon: ImageVector, val label: String, val onClick: () -> Unit)

/** «+» turns into «×» while the choices are out. */
@Composable
private fun AttachToggle(open: Boolean, onClick: () -> Unit) {
    val turn by animateFloatAsState(if (open) 45f else 0f, Blesk.motion.settle(), label = "attach")
    IconButton(
        icon = BleskIcons.Plus,
        contentDescription = stringResource(if (open) R.string.chat_cancel else R.string.media_attach),
        onClick = onClick,
        modifier = Modifier.graphicsLayer { rotationZ = turn },
    )
}

/** The attachment choices flow out of the lens one after another, as capsules. */
@Composable
private fun AttachMenu(choices: List<AttachChoice>, onChosen: () -> Unit) {
    val motion = Blesk.motion
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEachIndexed { index, choice ->
            val shown = remember { MutableTransitionState(motion.reduced).apply { targetState = true } }
            AnimatedVisibility(
                visibleState = shown,
                enter = scaleIn(motion.staggered(index), initialScale = 0.5f) + fadeIn(motion.staggered(index)),
            ) {
                Button(
                    text = choice.label,
                    icon = choice.icon,
                    kind = ButtonKind.Secondary,
                    onClick = {
                        onChosen()
                        choice.onClick()
                    },
                )
            }
        }
    }
}
