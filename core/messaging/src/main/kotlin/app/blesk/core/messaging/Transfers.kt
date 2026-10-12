package app.blesk.core.messaging

import app.blesk.core.data.Transfer
import app.blesk.core.network.BleskApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Moves encrypted files to and from the server in pieces, so a dropped connection costs one
 * piece, not the whole file: uploads ask the server which chunks it has, downloads continue from
 * the bytes already on disk. Progress is kept per [tag] — a message, or an album item.
 */
class Transfers {
    private val state = MutableStateFlow<Map<String, Transfer>>(emptyMap())

    val progress: StateFlow<Map<String, Transfer>> = state.asStateFlow()

    /** Uploads [file] and returns its attachment id. Retries on network errors with growing pauses. */
    suspend fun send(api: BleskApi, file: File, tag: String?): String = withContext(Dispatchers.IO) {
        val total = file.length()
        report(tag, 0, total)
        // A server without chunked uploads takes the file in one request.
        val started = api.startUpload(total) ?: return@withContext api.uploadAttachment(file).id.also { finish(tag) }
        val chunks = ((total + started.chunkBytes - 1) / started.chunkBytes).toInt()
        retrying {
            val have = api.uploadState(started.id).received.toSet()
            report(tag, have.size.toLong() * started.chunkBytes, total)
            RandomAccessFile(file, "r").use { source ->
                for (n in 0 until chunks) {
                    if (n in have) continue
                    val offset = n.toLong() * started.chunkBytes
                    val bytes = ByteArray(minOf(started.chunkBytes.toLong(), total - offset).toInt())
                    source.seek(offset)
                    source.readFully(bytes)
                    api.uploadChunk(started.id, n, bytes)
                    report(tag, minOf(total, offset + bytes.size), total)
                }
            }
        }
        api.completeUpload(started.id)
        finish(tag)
        started.id
    }

    /** Downloads attachment [id] into [partial], continuing after whatever is already there. */
    suspend fun fetch(api: BleskApi, id: String, partial: File, tag: String?) = withContext(Dispatchers.IO) {
        retrying {
            var done = partial.length()
            FileOutputStream(partial, true).use { output ->
                api.downloadFrom(id, done, output) { piece, total ->
                    done += piece
                    report(tag, done, total)
                }
            }
        }
        finish(tag)
    }

    /** Marks [tag] as stopped by an error, so the message can offer to try again. */
    fun fail(tag: String) = state.update { it + (tag to (it[tag] ?: Transfer(0, -1)).copy(failed = true)) }

    private fun report(tag: String?, done: Long, total: Long) {
        if (tag != null) state.update { it + (tag to Transfer(done, total)) }
    }

    private fun finish(tag: String?) {
        if (tag != null) state.update { it - tag }
    }

    private suspend fun retrying(block: suspend () -> Unit) {
        var pause = FIRST_PAUSE_MILLIS
        repeat(ATTEMPTS - 1) {
            try {
                return block()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: IOException) {
                delay(pause)
                pause *= 2
            }
        }
        block()
    }

    private companion object {
        const val ATTEMPTS = 6
        const val FIRST_PAUSE_MILLIS = 1_000L
    }
}
