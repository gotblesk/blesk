package app.blesk

import android.app.Application
import app.blesk.core.data.diagnostics.EventCode

class BleskApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashStore.install(container.eventLog)
        container.eventLog.record(EventCode.AppStarted, BuildConfig.VERSION_CODE.toLong())
    }
}
