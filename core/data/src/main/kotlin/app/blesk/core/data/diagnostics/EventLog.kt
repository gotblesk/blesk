package app.blesk.core.data.diagnostics

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Everything the debug journal may ever say. Entries are codes plus numbers, never free text,
 * so message text, keys and contacts cannot reach the journal even by mistake.
 */
enum class EventCode {
    AppStarted,
    ScreenOpened,
    ThemeChanged,
    ModeDemo,
    ModeServer,
    DebugUnlocked,
    CrashRecorded,
    CrashCleared,
    DemoMessageSent,
    DemoBotReplied,
    MessageQueued,
    SendFailed,
    DecryptFailed,
    KeyChanged,
    ServerSynced,
    KeysRefilled,
    ServerRegistered,
    BackgroundFailure,
    GroupMessageHeld,
    GroupMessageDropped,
    GroupUpdateRefused,
}

/** [value] is an index or a counter, never content. */
data class LogEntry(val atMillis: Long, val code: EventCode, val value: Long? = null) {
    /** «ScreenOpened 3»: the code and its number, nothing else. */
    val line: String get() = if (value == null) code.name else "${code.name} $value"
}

/** Where each entry goes besides the journal on screen. */
fun interface EventSink {
    fun write(entry: LogEntry)

    companion object {
        const val TAG = "blesk"

        /**
         * logcat under [TAG]: a release build has no `run-as`, so this is the only way the owner's
         * `logs` script gets the journal off a phone over adb.
         */
        val Logcat = EventSink { Log.i(TAG, it.line) }
    }
}

/** In-memory ring buffer of the latest [capacity] events, each also written to [sink]. */
class EventLog(
    private val capacity: Int = DEFAULT_CAPACITY,
    private val clock: () -> Long = System::currentTimeMillis,
    private val sink: EventSink = EventSink.Logcat,
) {
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    fun record(code: EventCode, value: Long? = null) {
        val entry = LogEntry(clock(), code, value)
        synchronized(buffer) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(entry)
            state.value = buffer.toList()
        }
        sink.write(entry)
    }

    fun clear() {
        synchronized(buffer) {
            buffer.clear()
            state.value = emptyList()
        }
    }

    companion object {
        const val DEFAULT_CAPACITY = 500
    }
}
