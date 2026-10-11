package app.blesk.server.spaces

import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.http.ApiException
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.EditPost
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route

private const val DEFAULT_PAGE = 50
private const val MAX_PAGE = 100
private const val MAX_CLIENT_ID_LENGTH = 128

/** Room for the post text and an attachment pointer with its thumbnail. */
private const val POST_REQUEST_LIMIT = 256L * 1024

internal fun Route.postRoutes(services: Services) {
    route("posts") {
        post {
            val space = call.space(services)
            val author = call.account
            val role = services.requireRole(space, author.id, SpaceRole.Member)
            // In a channel only the owner and admins publish; in a room every member writes.
            ensure(space.kind == SpaceKind.Room || role != SpaceRole.Member) { forbidden() }
            ensure(services.postRateLimiter.tryAcquire(author.id.toString())) {
                ApiException(HttpStatusCode.TooManyRequests, ErrorCodes.RATE_LIMITED)
            }
            val request = call.receiveJson<NewPost>(POST_REQUEST_LIMIT)
            ensure(request.clientId.isNotEmpty() && request.clientId.length <= MAX_CLIENT_ID_LENGTH) { badRequest() }
            ensure(request.text.isNotBlank() || request.attachment != null) { badRequest(message = "empty") }
            validText(request.text)
            ensure(services.spaces.channelExists(space.id, request.channel)) { notFound() }
            request.replyTo?.let { replyTo ->
                ensure(services.posts.find(space.id, replyTo)?.post?.channel == request.channel) { badRequest(message = "replyTo") }
            }
            request.attachment?.let { attachment ->
                ensure(services.attachments.exists(attachment.id)) { badRequest(ErrorCodes.UNKNOWN_ATTACHMENT) }
            }
            val draft = PostDraft(
                spaceId = space.id,
                channel = request.channel,
                authorId = author.id,
                clientId = request.clientId,
                text = request.text,
                replyTo = request.replyTo,
                attachment = request.attachment,
            )
            when (val created = services.posts.create(draft, services.now())) {
                is PostCreation.Created -> {
                    services.pushToMembers(space.id, ServerFrame.SpacePost(created.post))
                    call.respond(HttpStatusCode.Created, created.post)
                }
                is PostCreation.Duplicate -> call.respond(created.post)
            }
        }

        // Spaces are public: anyone signed in may read them without joining.
        get {
            val space = call.space(services)
            val channel = call.request.queryParameters["channel"] ?: NewPost.MAIN_CHANNEL
            ensure(services.spaces.channelExists(space.id, channel)) { notFound() }
            val before = call.request.queryParameters["before"]?.let { it.toLongOrNull().orFail { badRequest(message = "before") } }
            val limit = call.request.queryParameters["limit"]?.let { it.toIntOrNull().orFail { badRequest(message = "limit") } }
                ?: DEFAULT_PAGE
            ensure(limit > 0) { badRequest(message = "limit") }
            call.respond(services.posts.page(space.id, channel, before, limit.coerceAtMost(MAX_PAGE)))
        }

        patch("{id}") {
            val space = call.space(services)
            val stored = call.storedPost(services, space)
            ensure(stored.authorId == call.account.id) { forbidden() }
            val text = call.receiveJson<EditPost>(POST_REQUEST_LIMIT).text
            ensure(text.isNotBlank() || stored.post.attachment != null) { badRequest(message = "empty") }
            validText(text)
            val edited = services.posts.edit(stored.post.id, text, services.now())
            services.pushToMembers(space.id, ServerFrame.SpacePostChanged(space.handle, edited.id, edited))
            call.respond(edited)
        }

        delete("{id}") {
            val space = call.space(services)
            val stored = call.storedPost(services, space)
            if (stored.authorId != call.account.id) services.requireRole(space, call.account.id, SpaceRole.Admin)
            services.posts.delete(stored.post.id)
            services.pushToMembers(space.id, ServerFrame.SpacePostChanged(space.handle, stored.post.id, null))
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private suspend fun ApplicationCall.storedPost(services: Services, space: Space): StoredPost {
    val id = parameters["id"]?.toLongOrNull().orFail { notFound() }
    return services.posts.find(space.id, id).orFail { notFound() }
}

private fun validText(text: String) {
    ensure(text.length <= Spaces.MAX_POST) { badRequest(message = "text") }
}
