package app.blesk.server.http

import app.blesk.server.BleskJson
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.contentLength
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readBuffer
import kotlinx.io.readByteArray
import java.util.Base64

/** Plenty for every JSON request except a message, which sets its own limit. */
const val DEFAULT_JSON_LIMIT = 256L * 1024

/**
 * Reads at most [maxBytes] of JSON before decoding, so an oversized or endless body is cut off
 * instead of being buffered whole.
 */
suspend inline fun <reified T> ApplicationCall.receiveJson(maxBytes: Long = DEFAULT_JSON_LIMIT): T {
    val declared = request.contentLength()
    ensure(declared == null || declared <= maxBytes) { tooLarge() }
    val bytes = receiveChannel().readBuffer(maxBytes + 1).readByteArray()
    ensure(bytes.size <= maxBytes) { tooLarge() }
    return try {
        BleskJson.decodeFromString<T>(bytes.decodeToString())
    } catch (malformed: IllegalArgumentException) {
        // SerializationException is an IllegalArgumentException too.
        throw badRequest(message = malformed.javaClass.simpleName, cause = malformed)
    }
}

fun String.decodeBase64(code: String = ErrorCodes.BAD_REQUEST): ByteArray = try {
    Base64.getDecoder().decode(this)
} catch (malformed: IllegalArgumentException) {
    throw badRequest(code, "not base64", malformed)
}

fun ByteArray.encodeBase64(): String = Base64.getEncoder().encodeToString(this)
