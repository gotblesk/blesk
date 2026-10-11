package app.blesk.core.data.diagnostics

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
    ServerSynced,
    KeysRefilled,
    ServerRegistered,
    BackgroundFailure,
}

/** [value] is an index or a counter, never content. */
data class LogEntry(val atMillis: Long, val code: EventCode, val value: Long? = null)

/** In-memory ring buffer of the latest [capacity] events. */
class EventLog(private val capacity: Int = DEFAULT_CAPACITY, private val clock: () -> Long = System::currentTimeMillis) {
    private val buffer = ArrayDeque<LogEntry>(capacity)
    private val state = MutableStateFlow<List<LogEntry>>(emptyList())

    val entries: StateFlow<List<LogEntry>> = state.asStateFlow()

    fun record(code: EventCode, value: Long? = null) {
        synchronized(buffer) {
            if (buffer.size == capacity) buffer.removeFirst()
            buffer.addLast(LogEntry(clock(), code, value))
            state.value = buffer.toList()
        }
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
