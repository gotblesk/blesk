package app.blesk.feature.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.blesk.core.data.settings.Profile
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.HeaderArt
import app.blesk.core.design.component.HeaderStyle
import app.blesk.core.design.component.LocalHeaderFocus
import app.blesk.core.design.component.ProfileHero
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.TextInput
import app.blesk.core.design.component.chatBackdropKey
import app.blesk.core.design.component.chatBackdropOf
import app.blesk.core.design.component.headerFocusOf
import app.blesk.core.design.component.headerPhotoKey
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.glass.TopLens
import app.blesk.core.design.glass.lens
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
)

/**
 * Your profile, edited where it stands: the same header and avatar as in «Ты», with the fields
 * under them. «Сохранить» flows out of the top lens once something changed, and leaving with
 * changes asks first. A header photo keeps in view the place you tap on it.
 */
@Composable
fun ProfileScreen(
    profile: Profile,
    photo: ImageBitmap?,
    headerPhoto: ImageBitmap?,
    actions: ProfileActions,
    modifier: Modifier = Modifier,
) {
    val name = rememberTextFieldState(profile.name)
    val username = rememberTextFieldState(profile.username)
    val bio = rememberTextFieldState(profile.bio)
    val links = List(Profile.MAX_LINKS) { rememberTextFieldState(profile.links.getOrElse(it) { "" }) }
    var header by rememberSaveable(profile.header) { mutableStateOf(profile.header) }
    var saves by remember { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
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

    BackHandler(enabled = dirty && !leaving) { leaving = true }
    Box(modifier.fillMaxSize().background(Blesk.colors.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = 96.dp),
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
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                Button(stringResource(R.string.profile_photo), onClick = actions.onPickPhoto, kind = ButtonKind.Secondary)
                if (photo != null) {
                    Button(stringResource(R.string.profile_photo_remove), onClick = actions.onRemovePhoto, kind = ButtonKind.Secondary)
                }
            }
            TextInput(
                label = stringResource(R.string.profile_name),
                state = name,
                hint = stringResource(R.string.profile_counter, name.text.length, Profile.MAX_NAME),
                inputTransformation = InputTransformation.maxLength(Profile.MAX_NAME),
            )
            TextInput(
                label = stringResource(R.string.profile_username),
                state = username,
                hint = stringResource(R.string.profile_username_hint),
            )
            TextInput(
                label = stringResource(R.string.profile_bio),
                state = bio,
                singleLine = false,
                hint = stringResource(R.string.profile_counter, bio.text.length, Profile.MAX_BIO),
                inputTransformation = InputTransformation.maxLength(Profile.MAX_BIO),
            )
            // One more field appears as soon as the previous one is filled, up to three.
            links.forEachIndexed { index, link ->
                if (index == 0 || links[index - 1].text.isNotBlank()) {
                    TextInput(
                        label = stringResource(R.string.profile_link, index + 1),
                        state = link,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, autoCorrectEnabled = false),
                        inputTransformation = InputTransformation.maxLength(Profile.MAX_LINK),
                    )
                }
            }
            SectionHeader(stringResource(R.string.profile_header))
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
        TopLens(
            title = stringResource(R.string.profile_title),
            onBack = { if (dirty) leaving = true else actions.onBack() },
            collapsed = true,
            actions = {
                AnimatedVisibility(dirty, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
                    Button(stringResource(R.string.profile_save), onClick = ::save, modifier = Modifier.padding(end = 6.dp))
                }
            },
        )
        AnimatedVisibility(
            visible = leaving,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            LeaveCapsule(
                onDiscard = {
                    leaving = false
                    actions.onBack()
                },
                onStay = { leaving = false },
            )
        }
    }
}

/** Asks before changes are lost: «Отменить» throws them away, «Остаться» goes back to editing. */
@Composable
private fun LeaveCapsule(onDiscard: () -> Unit, onStay: () -> Unit) {
    Column(
        Modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(Blesk.dimens.gutter)
            .lens(RoundedCornerShape(Blesk.dimens.cardRadius))
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.profile_leave_title), style = Blesk.type.bodyStrong)
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(stringResource(R.string.profile_leave_discard), onClick = onDiscard, kind = ButtonKind.Secondary)
            Button(stringResource(R.string.profile_leave_stay), onClick = onStay)
        }
    }
}

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
