package app.blesk.navigation

import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.blesk.AppContainer
import app.blesk.feature.settings.EconomyScreen

@Composable
internal fun EconomyRoute(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    val monitor = container.economy
    val economy by monitor.economy.collectAsStateWithLifecycle()
    val active by monitor.active.collectAsStateWithLifecycle()
    val level by monitor.level.collectAsStateWithLifecycle()
    EconomyScreen(
        economy = economy,
        active = active,
        level = level,
        onChange = { next -> monitor.update { next } },
        onOpenSystemSaver = {
            runCatching { context.startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)) }
        },
        onBack = onBack,
    )
}
