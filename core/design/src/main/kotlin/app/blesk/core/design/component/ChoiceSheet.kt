package app.blesk.core.design.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.blesk.core.design.R
import app.blesk.core.design.theme.Blesk

/** One way out of a [ChoiceSheet]; [danger] marks the one that destroys something. */
class Choice(val label: String, val danger: Boolean = false, val onClick: () -> Unit)

/**
 * Instead of «Вы уверены?»: a matte capsule sheet at the bottom whose choices are the
 * confirmation itself — «Удалить у меня», «Удалить у всех» — and «Отмена». The destructive
 * choice is written in the danger colour, the others stay neutral. A tap outside, «назад» or
 * «Отмена» close it without doing anything.
 */
@Composable
fun ChoiceSheet(title: String, choices: List<Choice>, onDismiss: () -> Unit, text: String? = null) {
    BackHandler(onBack = onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = SCRIM))
            .dismissOnTap(onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Blesk.dimens.gutter)
                .blockTaps(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
                    .background(Blesk.colors.surface),
            ) {
                Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        title,
                        style = Blesk.type.bodyStrong,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().semantics { heading() },
                    )
                    if (text != null) {
                        Text(
                            text,
                            style = Blesk.type.label,
                            color = Blesk.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                        )
                    }
                }
                choices.forEach { choice ->
                    ChoiceRow(choice.label, if (choice.danger) Blesk.colors.danger else Blesk.colors.textPrimary) {
                        onDismiss()
                        choice.onClick()
                    }
                }
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
                    .background(Blesk.colors.surface),
            ) {
                ChoiceRow(stringResource(R.string.design_cancel), Blesk.colors.textPrimary, onDismiss)
            }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = Blesk.type.bodyStrong, color = color, textAlign = TextAlign.Center)
    }
}

private const val SCRIM = 0.35f
