package app.blesk.core.design.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.blesk.shared.appearance.UiDensity

@Immutable
data class BleskDimens(
    val gutter: Dp,
    val rowHeight: Dp,
    val rowGap: Dp,
    val sectionGap: Dp,
    val avatar: Dp,
    val capsuleRadius: Dp,
    val cardRadius: Dp,
    val hairline: Dp,
    /** Minimum touch target, kept at 48dp regardless of density for accessibility. */
    val touchTarget: Dp,
) {
    companion object {
        fun forDensity(density: UiDensity) = when (density) {
            UiDensity.Comfortable -> BleskDimens(
                gutter = 16.dp, rowHeight = 72.dp, rowGap = 6.dp, sectionGap = 28.dp,
                avatar = 48.dp, capsuleRadius = 28.dp, cardRadius = 22.dp, hairline = 1.dp, touchTarget = 48.dp,
            )
            UiDensity.Compact -> BleskDimens(
                gutter = 14.dp, rowHeight = 62.dp, rowGap = 4.dp, sectionGap = 22.dp,
                avatar = 42.dp, capsuleRadius = 24.dp, cardRadius = 18.dp, hairline = 1.dp, touchTarget = 48.dp,
            )
        }
    }
}
