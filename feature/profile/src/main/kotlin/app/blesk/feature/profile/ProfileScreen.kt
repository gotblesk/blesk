package app.blesk.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.insert
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.ActionCluster
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ClusterAction
import app.blesk.core.design.component.HeaderArt
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.LocalHeaderFocus
import app.blesk.core.design.component.ProfileHero
import app.blesk.core.design.component.ProfileTopLens
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.chatBackdropKey
import app.blesk.core.design.component.chatBackdropOf
import app.blesk.core.design.component.headerFocusOf
import app.blesk.core.design.component.headerPhotoKey
import app.blesk.core.design.component.nameIntoLens
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.glass.LensScaffold
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** What the edit leaves behind when saved. */
data class ProfileDraft(
    val name: String,
    val username: String,
    val bio: String,
    val header: String,
    val links: List<String> = emptyList(),
)

class ProfileActions(
    val onSave: (ProfileDraft) -> Unit,
    val onPickPhoto: () -> Unit,
    val onRemovePhoto: () -> Unit,
    val onPickHeaderPhoto: () -> Unit,
    val onBack: () -> Unit,
    /** Leaving with changes: no question asked, the changes go with an «Отменить» that brings them back. */
    val onLeaveUnsaved: (ProfileDraft) -> Unit = { onBack() },
)

/**
 * Your profile in edit mode: the very page of «Ты», not a form. The header, the avatar, the name,
 * @username, «о себе» and the links are changed where they stand, in the profile's own type;
 * a tap on the avatar picks a photo, «Шапка» opens the header choices under the cluster. Once
 * something changed, «Сохранить» rises from the bottom. [restored] is a draft brought back by
 * «Отменить» after leaving without saving.
 */
@Composable
fun ProfileScreen(
    profile: Profile,
    photo: ImageBitmap?,
    headerPhoto: ImageBitmap?,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
    restored: ProfileDraft? = null,
) {
    val start = restored ?: ProfileDraft(profile.name, profile.username, profile.bio, profile.header, profile.links)
    val name = rememberTextFieldState(start.name)
    val username = rememberTextFieldState(start.username)
    val bio = rememberTextFieldState(start.bio)
    val links = List(Profile.MAX_LINKS) { rememberTextFieldState(start.links.getOrElse(it) { "" }) }
    var header by rememberSaveable(start.header) { mutableStateOf(start.header) }
    var choosingHeader by rememberSaveable { mutableStateOf(false) }
    var saves by remember { mutableIntStateOf(0) }
    val haptics = rememberHaptics()
    val draft = ProfileDraft(
        name = name.text.toString(),
        username = Profile.normalizeUsername(username.text.toString()),
        bio = bio.text.toString(),
        header = header,
        links = Profile.cleanLinks(links.map { it.text.toString() }),
    )
    val dirty = draft != ProfileDraft(profile.name, profile.username, profile.bio, profile.header, profile.links)
    val style = chatBackdropOf(header) ?: HeaderStyle.Light(0)
    val scroll = rememberScrollState()

    fun save() {
        actions.onSave(draft)
        haptics.success()
        saves++
    }

    fun leave() = if (dirty) actions.onLeaveUnsaved(draft) else actions.onBack()

    BackHandler(enabled = dirty) { leave() }
    LensScaffold(
        modifier = modifier,
        top = { ProfileTopLens(stringResource(R.string.profile_title), { scroll.value }, onBack = ::leave) },
        bottom = { SaveCapsule(visible = dirty, onSave = ::save) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(bottom = padding.calculateBottomPadding() + SaveRoom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val focusHint = stringResource(R.string.profile_header_focus)
            CompositionLocalProvider(LocalHeaderFocus provides headerFocusOf(header)) {
                ProfileHero(
                    name = draft.name.ifBlank { "?" },
                    key = profile.username.ifBlank { profile.name },
                    photo = photo,
                    header = style,
                    headerPhoto = headerPhoto,
                    scroll = { scroll.value },
                    onAvatarClick = actions.onPickPhoto,
                    avatarClickLabel = stringResource(R.string.profile_photo_change),
                    modifier = Modifier
                        .flashOn(saves, RoundedCornerShape(0.dp))
                        .let { hero ->
                            if (style != HeaderStyle.Photo) return@let hero
                            // Tapping the photo picks the place that stays in view.
                            hero.semantics { contentDescription = focusHint }.pointerInput(Unit) {
                                detectTapGestures { at ->
                                    header = headerPhotoKey(at.x / size.width * 2 - 1, at.y / size.height * 2 - 1)
                                }
                            }
                        },
                )
            }
            InPlaceField(
                state = name,
                placeholder = stringResource(R.string.profile_name),
                style = Blesk.type.title,
                limit = Profile.MAX_NAME,
                modifier = Modifier.padding(top = 10.dp).nameIntoLens { scroll.value },
            )
            InPlaceField(
                state = username,
                placeholder = stringResource(R.string.profile_username),
                style = Blesk.type.label,
                color = Blesk.colors.textSecondary,
                prefix = "@",
                hint = stringResource(R.string.profile_username_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false),
            )
            InPlaceField(
                state = bio,
                placeholder = stringResource(R.string.profile_bio),
                style = Blesk.type.body,
                color = Blesk.colors.textSecondary,
                limit = Profile.MAX_BIO,
                singleLine = false,
                modifier = Modifier.padding(top = 6.dp),
            )
            val removeLabel = stringResource(R.string.profile_photo_remove)
            ActionCluster(
                buildList {
                    add(ClusterAction(BleskIcons.Camera, stringResource(R.string.profile_header_photo), actions.onPickPhoto))
                    add(ClusterAction(BleskIcons.Image, stringResource(R.string.profile_header)) { choosingHeader = !choosingHeader })
                    if (photo != null) add(ClusterAction(BleskIcons.Trash, removeLabel, actions.onRemovePhoto))
                },
                Modifier.padding(horizontal = Blesk.dimens.gutter, vertical = 16.dp),
            )
            AnimatedVisibility(choosingHeader, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Column {
                    HeaderPicker(
                        selected = style,
                        headerPhoto = headerPhoto,
                        onSelect = { header = if (it == HeaderStyle.Photo) headerPhotoKey(0f, 0f) else chatBackdropKey(it) },
                        onPickPhoto = actions.onPickHeaderPhoto,
                    )
                    if (style == HeaderStyle.Photo) {
                        Text(
                            text = focusHint,
                            style = Blesk.type.label,
                            color = Blesk.colors.textSecondary,
                            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            LinksCard(links)
        }
    }
}

/**
 * Text edited where it is shown: the same type and colour as on the profile, centred, with no
 * lines under it. While typing, a soft capsule shows behind the field; a counter or a [hint]
 * shows under it.
 */
@Composable
private fun InPlaceField(
    state: TextFieldState,
    placeholder: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Blesk.colors.textPrimary,
    prefix: String = "",
    limit: Int? = null,
    hint: String? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    var focused by remember { mutableStateOf(false) }
    val capsule by animateFloatAsState(if (focused) 1f else 0f, Blesk.motion.settle(), label = "fieldCapsule")
    val fill = Blesk.colors.surfaceStrong
    Column(
        modifier.fillMaxWidth().padding(horizontal = Blesk.dimens.gutter * 2),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicTextField(
            state = state,
            textStyle = style.copy(color = color, textAlign = TextAlign.Center),
            cursorBrush = SolidColor(Blesk.colors.accent),
            lineLimits = if (singleLine) TextFieldLineLimits.SingleLine else TextFieldLineLimits.MultiLine(maxHeightInLines = BIO_LINES),
            inputTransformation = limit?.let { InputTransformation.maxLength(it) },
            // The prefix is drawn as part of the text, so it stays right against it, centred together.
            outputTransformation = if (prefix.isEmpty()) null else OutputTransformation { if (length > 0) insert(0, prefix) },
            keyboardOptions = keyboardOptions,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = placeholder },
            decorator = { inner ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(FieldRadius))
                        .drawBehind { if (capsule > 0f) drawRect(fill.copy(alpha = fill.alpha * capsule)) }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.text.isEmpty()) Text(placeholder, style = style, color = Blesk.colors.textTertiary)
                    inner()
                }
            },
        )
        if (focused) {
            val note = hint ?: limit?.let { stringResource(R.string.profile_counter, state.text.length, it) }
            if (note != null) {
                Text(
                    text = note,
                    style = Blesk.type.caption,
                    color = Blesk.colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Up to three links, like on the profile; one more empty row appears once the last is filled. */
@Composable
private fun LinksCard(links: List<TextFieldState>) {
    val hint = stringResource(R.string.profile_link_hint)
    Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
        links.forEachIndexed { index, link ->
            if (index != 0 && links[index - 1].text.isBlank()) return@forEachIndexed
            val label = stringResource(R.string.profile_link, index + 1)
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(BleskIcons.Share, contentDescription = null, tint = Blesk.colors.textSecondary)
                BasicTextField(
                    state = link,
                    textStyle = Blesk.type.body.copy(color = Blesk.colors.textPrimary),
                    cursorBrush = SolidColor(Blesk.colors.accent),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    inputTransformation = InputTransformation.maxLength(Profile.MAX_LINK),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                    modifier = Modifier.weight(1f).semantics { contentDescription = label },
                    decorator = { inner ->
                        Box {
                            if (link.text.isEmpty()) Text(hint, color = Blesk.colors.textTertiary)
                            inner()
                        }
                    },
                )
            }
        }
    }
}

/** «Сохранить» rising from the bottom once something changed. */
@Composable
private fun SaveCapsule(visible: Boolean, onSave: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(Blesk.motion.settle()) { it } + fadeIn(),
        exit = slideOutVertically(Blesk.motion.settle()) { it } + fadeOut(),
    ) {
        Box(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(Blesk.dimens.gutter),
            contentAlignment = Alignment.Center,
        ) {
            Button(stringResource(R.string.profile_save), onClick = onSave, icon = BleskIcons.Check)
        }
    }
}

private val SaveRoom = 88.dp
private val FieldRadius = 20.dp
private const val BIO_LINES = 4

/** The six «Свет» compositions, the flat tones and your photo, each a small sample of the header. */
@Composable
private fun HeaderPicker(selected: HeaderStyle, headerPhoto: ImageBitmap?, onSelect: (HeaderStyle) -> Unit, onPickPhoto: () -> Unit) {
    val choices = buildList {
        repeat(HeaderStyle.Light.PRESETS) { add(HeaderStyle.Light(it)) }
        repeat(HeaderStyle.Solid.TONES) { add(HeaderStyle.Solid(it)) }
        if (headerPhoto != null) add(HeaderStyle.Photo)
    }
    val lightLabel = stringResource(R.string.profile_header_light)
    val solidLabel = stringResource(R.string.profile_header_solid)
    val photoLabel = stringResource(R.string.profile_header_photo)
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Blesk.dimens.gutter),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        choices.forEach { choice ->
            val label = when (choice) {
                is HeaderStyle.Light -> "$lightLabel ${choice.preset + 1}"
                is HeaderStyle.Solid -> "$solidLabel ${choice.tone + 1}"
                HeaderStyle.Photo -> photoLabel
            }
            val shape = RoundedCornerShape(14.dp)
            val chosen = choice == selected
            Box(
                Modifier
                    .size(width = 64.dp, height = 48.dp)
                    .clip(shape)
                    .border(if (chosen) 2.dp else 0.dp, if (chosen) Blesk.colors.accent else Blesk.colors.hairline, shape)
                    .clickable(role = Role.RadioButton, onClickLabel = label) { onSelect(choice) }
                    .semantics {
                        contentDescription = label
                        this.selected = chosen
                    },
            ) {
                HeaderArt(choice, headerPhoto, Modifier.fillMaxSize())
            }
        }
        Button(stringResource(R.string.profile_header_pick), onClick = onPickPhoto, kind = ButtonKind.Secondary)
    }
    Box(Modifier.height(4.dp))
}
