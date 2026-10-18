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
    const val INVALID_HANDLE = "invalid_handle"
    const val HANDLE_TAKEN = "handle_taken"
    const val FORBIDDEN = "forbidden"
    const val NOT_MEMBER = "not_member"
    const val BANNED = "banned"
    const val OWNER_CANNOT_LEAVE = "owner_cannot_leave"
    const val INVALID_ROLE = "invalid_role"
    const val NOT_A_ROOM = "not_a_room"
    const val TOO_MANY_CHANNELS = "too_many_channels"
    const val LAST_CHANNEL = "last_channel"
    const val UNKNOWN_ATTACHMENT = "unknown_attachment"
    const val INVALID_ROOM = "invalid_room"
    const val CALLS_UNAVAILABLE = "calls_unavailable"
    const val USERNAME_RESERVED = "username_reserved"
    const val INVALID_NAME = "invalid_name"
    const val TOO_MANY_BOTS = "too_many_bots"
    const val INVALID_TEXT = "invalid_text"
    const val INVALID_BUTTONS = "invalid_buttons"
    const val INVALID_COMMANDS = "invalid_commands"
    const val INVALID_WEBHOOK = "invalid_webhook"
    const val CHAT_NOT_STARTED = "chat_not_started"
    const val INVITE_REQUIRED = "invite_required"
    const val INVITE_INVALID = "invite_invalid"
    const val INVITE_LIMIT = "invite_limit"
}

fun badRequest(code: String = ErrorCodes.BAD_REQUEST, message: String = "", cause: Throwable? = null) =
    ApiException(HttpStatusCode.BadRequest, code, message, cause)

fun notFound(code: String = ErrorCodes.NOT_FOUND) = ApiException(HttpStatusCode.NotFound, code)

fun tooLarge(cause: Throwable? = null) = ApiException(HttpStatusCode.PayloadTooLarge, ErrorCodes.TOO_LARGE, cause = cause)

inline fun ensure(condition: Boolean, failure: () -> ApiException) {
    if (!condition) throw failure()
}

inline fun <T : Any> T?.orFail(failure: () -> ApiException): T = this ?: throw failure()

fun forbidden(code: String = ErrorCodes.FORBIDDEN) = ApiException(HttpStatusCode.Forbidden, code)

fun conflict(code: String) = ApiException(HttpStatusCode.Conflict, code)

fun rateLimited() = ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED)
