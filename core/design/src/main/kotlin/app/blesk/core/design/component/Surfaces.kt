package app.blesk.core.design.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.blesk.core.design.theme.Blesk

/** The capsule every list item and card sits in. Matte unless [protected]. */
@Composable
fun Capsule(
    modifier: Modifier = Modifier,
    protected: Boolean = false,
    shape: Shape = RoundedCornerShape(Blesk.dimens.capsuleRadius),
    color: Color = Blesk.colors.surface,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .let {
                when {
                    onClick != null && onLongClick != null -> it.combinedClickable(
                        onClickLabel = onClickLabel,
                        role = Role.Button,
                        onLongClickLabel = onLongClickLabel,
                        onLongClick = onLongClick,
                        onClick = onClick,
                    )
                    onClick != null -> it.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    else -> it
                }
            }
            .glint(shape, enabled = protected),
        content = content,
    )
}

/** A group of rows on one matte card. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Blesk.dimens.cardRadius))
            .background(Blesk.colors.surface)
            .padding(vertical = 4.dp),
        content = content,
    )
}
