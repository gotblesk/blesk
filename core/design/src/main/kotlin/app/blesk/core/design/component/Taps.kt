package app.blesk.core.design.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

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
