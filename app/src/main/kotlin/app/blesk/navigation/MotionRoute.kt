package app.blesk.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.design.component.Avatar
import app.blesk.core.design.component.Switch
import app.blesk.core.design.component.glint
import app.blesk.core.design.fire.beacon
import app.blesk.core.design.fire.breathing
import app.blesk.core.design.fire.flashOn
import app.blesk.core.design.fire.lightSweep
import app.blesk.core.design.spark.KeyMerge
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.Durations
import app.blesk.core.motion.rememberHaptics
import app.blesk.feature.chats.ChatMotion
import app.blesk.feature.chats.ChatMotionDemo
import app.blesk.feature.debug.HapticDemo
import app.blesk.feature.debug.MotionDemo
import app.blesk.feature.debug.MotionShowcase
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import app.blesk.feature.debug.R as DebugR

@Serializable
data object MotionKey : NavKey

/** «Витрина движения»: the brief's animations and vibrations, gathered from every module that draws them. */
@Composable
internal fun MotionRoute(container: AppContainer, onOpenChat: (String) -> Unit, onBack: () -> Unit) {
    val haptics = rememberHaptics()

    @Composable
    fun chat(title: Int, note: Int, motion: ChatMotion) =
        MotionDemo(stringResource(title), stringResource(note)) { replay -> ChatMotionDemo(motion, replay) }

    @Composable
    fun haptic(title: Int, note: Int, play: () -> Unit) = HapticDemo(stringResource(title), stringResource(note), play)

    val animations = persistentListOf(
        chat(DebugR.string.anim_send, DebugR.string.anim_send_note, ChatMotion.Send),
        chat(DebugR.string.anim_receive, DebugR.string.anim_receive_note, ChatMotion.Receive),
        chat(DebugR.string.anim_status, DebugR.string.anim_status_note, ChatMotion.Status),
        chat(DebugR.string.anim_thread, DebugR.string.anim_thread_note, ChatMotion.Thread),
        chat(DebugR.string.anim_reaction, DebugR.string.anim_reaction_note, ChatMotion.Reaction),
        chat(DebugR.string.anim_delete, DebugR.string.anim_delete_note, ChatMotion.Delete),
        chat(DebugR.string.anim_melt, DebugR.string.anim_melt_note, ChatMotion.Melt),
        chat(DebugR.string.anim_failure, DebugR.string.anim_failure_note, ChatMotion.Failure),
        chat(DebugR.string.anim_unread, DebugR.string.anim_unread_note, ChatMotion.Unread),
        MotionDemo(stringResource(DebugR.string.anim_open_chat), stringResource(DebugR.string.anim_open_chat_note)) { replay ->
            LaunchedEffect(replay) { if (replay > 0) onOpenChat(DemoSeed.ANYA) }
        },
        MotionDemo(stringResource(DebugR.string.anim_theme), stringResource(DebugR.string.anim_theme_note)) { replay ->
            LaunchedEffect(replay) {
                if (replay == 0) return@LaunchedEffect
                // Over to a theme of the other lightness and, once the wave has passed, back.
                val current = container.settings.appearance.first()
                val other = ThemeSpec.presets.first { it.isDark != current.theme.isDark }
                container.settings.updateAppearance { it.copy(theme = other) }
                delay(THEME_HOLD_MILLIS)
                container.settings.updateAppearance { it.copy(theme = current.theme) }
            }
        },
        MotionDemo(stringResource(DebugR.string.anim_keys), stringResource(DebugR.string.anim_keys_note)) { replay ->
            KeyMerge(matched = replay % 2 == 1 || replay == 0, replay = replay)
        },
        MotionDemo(stringResource(DebugR.string.anim_sweep), stringResource(DebugR.string.anim_sweep_note)) { replay ->
            key(replay) { Capsule(Modifier.lightSweep(active = replay > 0, shape = CircleShape)) }
        },
        MotionDemo(stringResource(DebugR.string.anim_wipe), stringResource(DebugR.string.anim_wipe_note)) { replay ->
            FadingGlints(replay)
        },
        MotionDemo(stringResource(DebugR.string.anim_beacon), stringResource(DebugR.string.anim_beacon_note)) { replay ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Avatar("Аня Соколова", size = 72.dp, modifier = Modifier.beacon(active = replay % 2 == 1))
            }
        },
        MotionDemo(stringResource(DebugR.string.anim_breath), stringResource(DebugR.string.anim_breath_note)) { replay -> Breath(replay) },
        MotionDemo(stringResource(DebugR.string.anim_fire), stringResource(DebugR.string.anim_fire_note)) { replay ->
            Capsule(Modifier.flashOn(key = replay, shape = CircleShape))
        },
        chat(DebugR.string.anim_read_more, DebugR.string.anim_read_more_note, ChatMotion.ReadMore),
        MotionDemo(stringResource(DebugR.string.anim_toggle), stringResource(DebugR.string.anim_toggle_note)) { replay ->
            Switch(checked = replay % 2 == 1)
        },
    )
    val vibrations = persistentListOf(
        haptic(DebugR.string.haptic_tick, DebugR.string.haptic_tick_note, haptics::tick),
        haptic(DebugR.string.haptic_stick, DebugR.string.haptic_stick_note, haptics::stick),
        haptic(DebugR.string.haptic_detach, DebugR.string.haptic_detach_note, haptics::detach),
        haptic(DebugR.string.haptic_success, DebugR.string.haptic_success_note, haptics::success),
        haptic(DebugR.string.haptic_refuse, DebugR.string.haptic_refuse_note, haptics::refuse),
        haptic(DebugR.string.haptic_warn, DebugR.string.haptic_warn_note, haptics::warn),
        haptic(DebugR.string.haptic_heavy, DebugR.string.haptic_heavy_note, haptics::heavy),
        haptic(DebugR.string.haptic_menu, DebugR.string.haptic_menu_note, haptics::menu),
        haptic(DebugR.string.haptic_toggle_on, DebugR.string.haptic_toggle_on_note) { haptics.toggle(on = true) },
        haptic(DebugR.string.haptic_toggle_off, DebugR.string.haptic_toggle_off_note) { haptics.toggle(on = false) },
        haptic(DebugR.string.haptic_beacon, DebugR.string.haptic_beacon_note, haptics::beacon),
        haptic(DebugR.string.haptic_call_end, DebugR.string.haptic_call_end_note, haptics::callEnded),
    )
    MotionShowcase(animations = animations, haptics = vibrations, onBack = onBack)
}

@Composable
private fun Capsule(modifier: Modifier = Modifier) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(CapsuleHeight)
            .clip(CircleShape)
            .background(Blesk.colors.surfaceStrong)
            .then(modifier),
    )
}

/** A voice that rises and falls for a few seconds, and the avatar's edge breathing with it. */
@Composable
private fun Breath(replay: Int) {
    var level by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(replay) {
        if (replay == 0) return@LaunchedEffect
        VOICE.forEach { loud ->
            level = loud
            delay(VOICE_STEP_MILLIS)
        }
        level = 0f
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Avatar("Лера", size = 72.dp, modifier = Modifier.breathing(level))
    }
}

/** «Удалить всё»: the glints go out one after another. */
@Composable
private fun FadingGlints(replay: Int) {
    val glints = remember(replay) { List(GLINTS) { Animatable(1f) } }
    LaunchedEffect(replay) {
        if (replay == 0) return@LaunchedEffect
        glints.forEach { glint ->
            delay(Durations.STAGGER * 2L)
            launch { glint.animateTo(0f) }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        glints.forEach { glint ->
            Box(
                Modifier
                    .weight(1f)
                    .height(CapsuleHeight)
                    .clip(CircleShape)
                    .background(Blesk.colors.surfaceStrong)
                    .glint(CircleShape, intensity = { glint.value }),
            )
        }
    }
}

private val CapsuleHeight = 48.dp
private val VOICE = listOf(0.3f, 0.8f, 0.5f, 1f, 0.2f, 0.7f, 0.9f, 0.4f, 0f, 0.6f, 1f, 0.3f)
private const val VOICE_STEP_MILLIS = 220L
private const val GLINTS = 4
private const val THEME_HOLD_MILLIS = 1_800L
