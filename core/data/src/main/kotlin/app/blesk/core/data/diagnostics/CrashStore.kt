package app.blesk.core.data.diagnostics

import java.io.File
import java.io.IOException

/**
 * Keeps the last crash on the device and nowhere else. Exception messages are dropped and
 * only class names and stack frames are written, because a message can quote user data.
 */
class CrashStore(directory: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val file = File(directory, "last_crash.txt")

    fun install(log: EventLog) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(error) }
            log.record(EventCode.CrashRecorded)
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(): CrashReport? {
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            val at = lines.firstOrNull()?.toLongOrNull() ?: return null
            CrashReport(at, lines.drop(1).joinToString("\n"))
        } catch (_: IOException) {
            null
        }
    }

    fun clear() {
        file.delete()
    }

    internal fun write(error: Throwable) {
        file.parentFile?.mkdirs()
        file.writeText(clock().toString() + "\n" + sanitize(error))
    }

    companion object {
        private const val MAX_CAUSES = 8

        fun sanitize(error: Throwable): String = buildString {
            var current: Throwable? = error
            var depth = 0
            while (current != null && depth < MAX_CAUSES) {
                if (depth > 0) append("Caused by: ")
                append(current.javaClass.name).append('\n')
                current.stackTrace.forEach { append("    at ").append(it.toString()).append('\n') }
                current = current.cause?.takeIf { it !== current }
                depth++
            }
        }
    }
}

data class CrashReport(val atMillis: Long, val trace: String)
