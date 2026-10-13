package app.blesk.navigation

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import app.blesk.AppContainer
import app.blesk.BuildConfig
import app.blesk.core.data.settings.AppMode
import app.blesk.core.design.motion.screenKey
import app.blesk.core.design.motion.sharedBounds
import app.blesk.feature.settings.DevicesScreen
import app.blesk.feature.settings.ThisDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data object DevicesKey : NavKey

@Composable
internal fun DevicesRoute(container: AppContainer, onBack: () -> Unit) {
    val mode by container.settings.mode.collectAsStateWithLifecycle(initialValue = AppMode.Demo)
    val context = LocalContext.current
    val model by produceState(Build.MODEL) { value = withContext(Dispatchers.IO) { deviceName(context) } }
    val device = ThisDevice(
        model = model,
        android = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        app = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
    )
    Box(Modifier.sharedBounds(screenKey("devices"))) {
        DevicesScreen(device = device, demo = mode == AppMode.Demo, onBack = onBack)
    }
}
