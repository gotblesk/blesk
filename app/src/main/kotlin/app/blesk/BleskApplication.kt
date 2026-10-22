package app.blesk

import android.app.Application
import app.blesk.background.MessageNotifier
import app.blesk.background.PlaybackService
import app.blesk.background.TransferService
import app.blesk.calls.CallCoordinator
import app.blesk.core.data.diagnostics.EventCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Instant

open class BleskApplication : Application() {
    lateinit var container: AppContainer
        private set

    /** The app's «now»; screenshot tests stop it at one moment so dates on screen never move. */
    protected open val clock: () -> Instant = Instant::now

    internal lateinit var notifier: MessageNotifier
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this, clock)
        container.crashStore.install(container.eventLog)
        container.eventLog.record(EventCode.AppStarted, BuildConfig.VERSION_CODE.toLong())
        registerActivityLifecycleCallbacks(VisibleActivities { container.visible = it })
        CallCoordinator(this, container).start()
        notifier = MessageNotifier(this, container).also { it.start() }
        // The first song or voice note brings up the system player, so playback survives the background.
        container.scope.launch {
            container.audioPlayer.playback.map { it.playing }.distinctUntilChanged().filter { it }.first()
            PlaybackService.connect(this@BleskApplication)
        }
        // Files keep moving in the background under a notification while any are on their way.
        container.scope.launch {
            container.chats.flatMapLatest { it?.transfers ?: flowOf(emptyMap()) }
                .map { transfers -> transfers.values.any { !it.failed } }
                .distinctUntilChanged()
                .collect { busy -> if (busy) TransferService.start(this@BleskApplication) }
        }
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
