package app.blesk.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * The dimmed area around a sheet or menu: a tap anywhere on it closes what it surrounds. It is
 * not a control of its own, so it does not shrink or light up under the finger.
 */
@Suppress("ClickWithoutResponse")
@Composable
fun Modifier.dismissOnTap(onDismiss: () -> Unit, label: String? = null): Modifier =
    clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClickLabel = label, onClick = onDismiss)

/** A surface lying over a [dismissOnTap] area: taps on it stop here instead of closing it. */
@Suppress("ClickWithoutResponse")
@Composable
fun Modifier.blockTaps(): Modifier = clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}

/**
 * A surface drawn over a [dismissOnTap] area beside it, not inside it: a tap on it stops here, as on anything drawn on
 * top, and does not close it. Unlike [blockTaps] it is no button, so the surface's title and rows stay apart for TalkBack.
 */
fun Modifier.catchesTaps(): Modifier = pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } }
