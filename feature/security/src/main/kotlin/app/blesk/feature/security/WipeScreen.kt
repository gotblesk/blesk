package app.blesk.feature.security

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SlideToConfirm
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk
import app.blesk.core.motion.rememberHaptics

/** What is being erased: only this phone, or the account as well. */
enum class WipeKind { Phone, DeleteAccount, ForgotCode }

@Composable
fun WipeScreen(onWipe: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier, kind: WipeKind = WipeKind.Phone) {
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { haptics.warn() }
    val (title, text, action) = when (kind) {
        WipeKind.Phone -> Triple(R.string.wipe_title, R.string.wipe_text, R.string.wipe_action)
        WipeKind.ForgotCode -> Triple(R.string.lock_forgot_title, R.string.lock_forgot_text, R.string.wipe_action)
        WipeKind.DeleteAccount -> Triple(R.string.delete_account_title, R.string.delete_account_text, R.string.delete_account_action)
    }
    ScrollScreen(title = stringResource(title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(text),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp, vertical = 16.dp),
        )
        SlideToConfirm(
            label = stringResource(R.string.wipe_slide),
            actionLabel = stringResource(action),
            onConfirm = onWipe,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter).padding(top = 24.dp),
        )
    }
}
