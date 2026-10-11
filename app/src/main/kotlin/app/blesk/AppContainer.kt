package app.blesk

import android.content.Context
import app.blesk.core.data.demo.DemoRepository
import app.blesk.core.data.demo.DemoSeed
import app.blesk.core.data.diagnostics.CrashStore
import app.blesk.core.data.diagnostics.EventLog
import app.blesk.core.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File
import java.time.Instant

/** Process-wide dependencies, built once in [BleskApplication]. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val eventLog = EventLog()
    val crashStore = CrashStore(File(context.noBackupFilesDir, "diagnostics"))
    val settings = SettingsStore(context)
    val demo: DemoRepository by lazy {
        DemoRepository(DemoSeed.from(context.resources, Instant.now()), scope, eventLog)
    }
}
