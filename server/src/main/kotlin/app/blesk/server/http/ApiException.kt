package app.blesk.server.http

import io.ktor.http.HttpStatusCode

/** A failure the client is told about as an [app.blesk.shared.protocol.ErrorBody] with a stable [code]. */
class ApiException(
    val status: HttpStatusCode,
    val code: String,
    message: String = "",
    cause: Throwable? = null,
) : RuntimeException(message, cause)

object ErrorCodes {
    const val BAD_REQUEST = "bad_request"
    const val INVALID_USERNAME = "invalid_username"
    const val INVALID_KEY = "invalid_key"
    const val INVALID_SIGNATURE = "invalid_signature"
    const val USERNAME_TAKEN = "username_taken"
    const val UNAUTHORIZED = "unauthorized"
    const val AUTH_FAILED = "auth_failed"
    const val NOT_FOUND = "not_found"
    const val NO_KEYS = "no_keys"
    const val TOO_MANY_KEYS = "too_many_keys"
    const val TOO_LARGE = "too_large"
    const val RATE_LIMITED = "rate_limited"
    const val INTERNAL = "internal"
}

fun badRequest(code: String = ErrorCodes.BAD_REQUEST, message: String = "", cause: Throwable? = null) =
    ApiException(HttpStatusCode.BadRequest, code, message, cause)

fun notFound(code: String = ErrorCodes.NOT_FOUND) = ApiException(HttpStatusCode.NotFound, code)

fun tooLarge(cause: Throwable? = null) = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.TOO_LARGE, cause = cause)

inline fun ensure(condition: Boolean, failure: () -> ApiException) {
    if (!condition) throw failure()
}

inline fun <T : Any> T?.orFail(failure: () -> ApiException): T = this ?: throw failure()
