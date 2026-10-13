package app.blesk.economy

import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** Holds the screen at 60 Hz while the economy mode asks for it; 0 lets the system choose again. */
@Composable
internal fun EconomyWindow(window: Window, cap60Hz: Boolean) {
    LaunchedEffect(cap60Hz) {
        window.attributes = window.attributes.apply { preferredRefreshRate = if (cap60Hz) CAPPED_HZ else 0f }
    }
}

private const val CAPPED_HZ = 60f
