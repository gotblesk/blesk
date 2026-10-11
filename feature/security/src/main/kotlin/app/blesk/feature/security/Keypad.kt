package app.blesk.feature.security

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.blesk.core.design.component.Icon
import app.blesk.core.design.component.Text
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk

/** Filled drops for entered digits, hollow for the rest. Shakes on a wrong code. */
@Composable
internal fun PinDots(length: Int, entered: Int, shake: Animatable<Float, *>, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.lock_entered, entered, length)
    Row(
        modifier = modifier
            .graphicsLayer { translationX = shake.value }
            .semantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(length) { index ->
            val filled by animateFloatAsState(if (index < entered) 1f else 0f, spring(dampingRatio = 0.6f), label = "dot")
            Box(
                Modifier
                    .size(14.dp)
                    .graphicsLayer {
                        scaleX = 0.8f + 0.2f * filled
                        scaleY = 0.8f + 0.2f * filled
                    }
                    .border(1.5.dp, Blesk.colors.textSecondary, CircleShape)
                    .background(Blesk.colors.textPrimary.copy(alpha = filled), CircleShape),
            )
        }
    }
}

/** Twelve round matte keys: digits, an optional biometric key and delete. */
@Composable
internal fun Keypad(
    onDigit: (Char) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onBiometric: (() -> Unit)? = null,
) {
    val rows = listOf("123", "456", "789")
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { digit -> Key(label = digit.toString(), onClick = { onDigit(digit) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (onBiometric != null) {
                IconKey(description = stringResource(R.string.lock_biometric), onClick = onBiometric) {
                    Icon(BleskIcons.Fingerprint, contentDescription = null)
                }
            } else {
                Box(Modifier.size(KeySize))
            }
            Key(label = "0", onClick = { onDigit('0') })
            IconKey(description = stringResource(R.string.lock_delete), onClick = onDelete) {
                Icon(BleskIcons.Backspace, contentDescription = null)
            }
        }
    }
}

private val KeySize = 72.dp

@Composable
private fun Key(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(Blesk.colors.surface)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Blesk.type.title, modifier = Modifier.clearAndSetSemantics { contentDescription = label })
    }
}

@Composable
private fun IconKey(description: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
