package app.blesk

import android.app.Application
import app.blesk.background.MessageNotifier
import app.blesk.calls.CallCoordinator
import app.blesk.core.data.diagnostics.EventCode

class BleskApplication : Application() {
    lateinit var container: AppContainer
        private set

    internal lateinit var notifier: MessageNotifier
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.crashStore.install(container.eventLog)
        container.eventLog.record(EventCode.AppStarted, BuildConfig.VERSION_CODE.toLong())
        registerActivityLifecycleCallbacks(VisibleActivities { container.visible = it })
        CallCoordinator(this, container).start()
        notifier = MessageNotifier(this, container).also { it.start() }
    }
}

/** Tells whether any of our screens is in front of the user. */
private class VisibleActivities(private val onChange: (Boolean) -> Unit) : android.app.Application.ActivityLifecycleCallbacks {
    private var started = 0

    override fun onActivityStarted(activity: android.app.Activity) {
        started++
        onChange(true)
    }

    override fun onActivityStopped(activity: android.app.Activity) {
        started = (started - 1).coerceAtLeast(0)
        onChange(started > 0)
    }

    override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) = Unit

    override fun onActivityResumed(activity: android.app.Activity) = Unit

    override fun onActivityPaused(activity: android.app.Activity) = Unit

    override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) = Unit

    override fun onActivityDestroyed(activity: android.app.Activity) = Unit
}
