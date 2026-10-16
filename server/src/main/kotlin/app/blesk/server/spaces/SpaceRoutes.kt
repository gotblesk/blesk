package app.blesk.server.spaces

import app.blesk.server.Services
import app.blesk.server.auth.account
import app.blesk.server.http.ErrorCodes
import app.blesk.server.http.badRequest
import app.blesk.server.http.conflict
import app.blesk.server.http.ensure
import app.blesk.server.http.forbidden
import app.blesk.server.http.notFound
import app.blesk.server.http.orFail
import app.blesk.server.http.receiveJson
import app.blesk.shared.protocol.CreateSpace
import app.blesk.shared.protocol.CreateSpaceChannel
import app.blesk.shared.protocol.NewPost
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.ServerFrame
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.TextSafety
import app.blesk.shared.protocol.UpdateSpace
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import java.security.SecureRandom

private const val SEARCH_LIMIT = 30
private const val DEFAULT_MEMBER_PAGE = 100
private const val MAX_MEMBER_PAGE = 200
private const val CHANNEL_ID_LENGTH = 8
private const val ROOM_FIRST_CHANNEL_ID = "general"
private const val ROOM_FIRST_CHANNEL_TITLE = "общее"

/** Fixed paths under /v1/spaces that would otherwise be read as a handle. */
private val ReservedHandles = setOf("mine")

private val random = SecureRandom()

fun Route.spaceRoutes(services: Services) {
    route(Spaces.PATH) {
        post {
            val request = call.receiveJson<CreateSpace>()
            ensure(Spaces.isValidHandle(request.handle) && request.handle !in ReservedHandles) { badRequest(ErrorCodes.INVALID_HANDLE) }
            val title = validTitle(request.title)
            val about = validAbout(request.about)
            val firstChannel = when (request.kind) {
                SpaceKind.Channel -> SpaceChannel(NewPost.MAIN_CHANNEL, title)
                SpaceKind.Room -> SpaceChannel(ROOM_FIRST_CHANNEL_ID, ROOM_FIRST_CHANNEL_TITLE)
            }
            val owner = call.account.id
            val created = services.spaces.create(request.handle, request.kind, title, about, owner, firstChannel, services.now())
            ensure(created) { conflict(ErrorCodes.HANDLE_TAKEN) }
            val space = checkNotNull(services.spaces.find(request.handle))
            call.respond(HttpStatusCode.Created, services.spaces.info(space, owner))
        }

        get {
            val query = call.request.queryParameters["query"].orEmpty().trim()
            call.respond(services.spaces.search(query, call.account.id, SEARCH_LIMIT))
        }

        get("mine") {
            call.respond(services.spaces.joined(call.account.id))
        }

        route("{handle}") {
            get {
                call.respond(services.spaces.info(call.space(services), call.account.id))
            }

            patch {
                val space = call.space(services)
                services.requireRole(space, call.account.id, SpaceRole.Admin)
                val request = call.receiveJson<UpdateSpace>()
                services.spaces.update(space.id, request.title?.let(::validTitle), request.about?.let(::validAbout))
                call.respond(services.spaces.info(checkNotNull(services.spaces.find(space.handle)), call.account.id))
            }

            delete {
                val space = call.space(services)
                services.requireRole(space, call.account.id, SpaceRole.Owner)
                services.spaces.delete(space.id)
                call.respond(HttpStatusCode.NoContent)
            }

            membershipRoutes(services)
            moderationRoutes(services)
            channelRoutes(services)
            postRoutes(services)
        }
    }
}

private fun Route.membershipRoutes(services: Services) {
    post("join") {
        val space = call.space(services)
        ensure(services.spaces.join(space.id, call.account.id, services.now())) { forbidden(ErrorCodes.BANNED) }
        call.respond(services.spaces.info(space, call.account.id))
    }

    post("leave") {
        val space = call.space(services)
        val role = services.spaces.role(space.id, call.account.id)
        ensure(role != SpaceRole.Owner) { conflict(ErrorCodes.OWNER_CANNOT_LEAVE) }
        services.spaces.leave(space.id, call.account.id)
        call.respond(HttpStatusCode.NoContent)
    }

    get("members") {
        val space = call.space(services)
        services.requireRole(space, call.account.id, SpaceRole.Member)
        val offset = call.intParameter("offset", 0)
        val limit = call.intParameter("limit", DEFAULT_MEMBER_PAGE).coerceAtMost(MAX_MEMBER_PAGE)
        ensure(offset >= 0 && limit > 0) { badRequest() }
        call.respond(services.spaces.members(space.id, offset, limit))
    }
}

private fun Route.moderationRoutes(services: Services) {
    post("roles") {
        val space = call.space(services)
        val change = call.receiveJson<RoleChange>()
        ensure(change.role != SpaceRole.Owner) { badRequest(ErrorCodes.INVALID_ROLE) }
        val callerRole = services.requireRole(space, call.account.id, SpaceRole.Admin)
        val target = services.accounts.find(change.username).orFail { notFound() }
        val targetRole = services.spaces.role(space.id, target.id).orFail { notFound(ErrorCodes.NOT_MEMBER) }
        ensure(outranks(callerRole, targetRole)) { forbidden() }
        services.spaces.setRole(space.id, target.id, change.role)
        call.respond(HttpStatusCode.NoContent)
    }

    route("ban/{username}") {
        post {
            val space = call.space(services)
            val callerRole = services.requireRole(space, call.account.id, SpaceRole.Admin)
            val target = services.accounts.find(call.parameters["username"].orEmpty()).orFail { notFound() }
            val targetRole = services.spaces.role(space.id, target.id)
            ensure(targetRole == null || outranks(callerRole, targetRole)) { forbidden() }
            services.spaces.ban(space.id, target.id)
            call.respond(HttpStatusCode.NoContent)
        }

        delete {
            val space = call.space(services)
            services.requireRole(space, call.account.id, SpaceRole.Admin)
            val target = services.accounts.find(call.parameters["username"].orEmpty()).orFail { notFound() }
            services.spaces.unban(space.id, target.id)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

private fun Route.channelRoutes(services: Services) {
    post("channels") {
        val space = call.space(services)
        ensure(space.kind == SpaceKind.Room) { badRequest(ErrorCodes.NOT_A_ROOM) }
        services.requireRole(space, call.account.id, SpaceRole.Admin)
        val title = validTitle(call.receiveJson<CreateSpaceChannel>().title)
        val added = services.spaces.addChannel(space.id, SpaceChannel(newChannelId(), title), Spaces.MAX_CHANNELS)
        ensure(added) { conflict(ErrorCodes.TOO_MANY_CHANNELS) }
        call.respond(services.spaces.info(space, call.account.id))
    }

    delete("channels/{id}") {
        val space = call.space(services)
        services.requireRole(space, call.account.id, SpaceRole.Admin)
        when (services.spaces.removeChannel(space.id, call.parameters["id"].orEmpty())) {
            ChannelRemoval.Removed -> call.respond(HttpStatusCode.NoContent)
            ChannelRemoval.NotFound -> throw notFound()
            ChannelRemoval.Last -> throw conflict(ErrorCodes.LAST_CHANNEL)
        }
    }
}

internal suspend fun ApplicationCall.space(services: Services): Space {
    val handle = parameters["handle"].orEmpty()
    ensure(Spaces.isValidHandle(handle)) { notFound() }
    return services.spaces.find(handle).orFail { notFound() }
}

/**
 * The caller's role, provided it is at least [minimum] (Owner > Admin > Member). Non-members get
 * not_member, members without enough rights get forbidden.
 */
internal suspend fun Services.requireRole(space: Space, accountId: Long, minimum: SpaceRole): SpaceRole {
    val role = spaces.role(space.id, accountId).orFail { forbidden(ErrorCodes.NOT_MEMBER) }
    ensure(role.ordinal <= minimum.ordinal) { forbidden() }
    return role
}

internal suspend fun Services.pushToMembers(spaceId: Long, frame: ServerFrame) {
    spaces.memberIds(spaceId).forEach { hub.push(it, frame) }
}

/** Moderators act only on people strictly below them: an admin cannot touch another admin or the owner. */
internal fun outranks(caller: SpaceRole, target: SpaceRole) = caller.ordinal < target.ordinal

/** Cleaned as on the phone; a title of nothing but invisible characters is no title. */
private fun validTitle(title: String): String {
    val cleaned = TextSafety.clean(title)
    ensure(TextSafety.hasVisible(cleaned) && TextSafety.visibleLength(cleaned) <= Spaces.MAX_TITLE) { badRequest(message = "title") }
    return cleaned
}

private fun validAbout(about: String): String {
    val cleaned = TextSafety.clean(about, multiline = true)
    ensure(TextSafety.visibleLength(cleaned) <= Spaces.MAX_ABOUT) { badRequest(message = "about") }
    return cleaned
}

private fun ApplicationCall.intParameter(name: String, default: Int): Int {
    val raw = request.queryParameters[name] ?: return default
    return raw.toIntOrNull().orFail { badRequest(message = name) }
}

private fun newChannelId(): String =
    (1..CHANNEL_ID_LENGTH).map { CHANNEL_ALPHABET[random.nextInt(CHANNEL_ALPHABET.length)] }.joinToString("")

private const val CHANNEL_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
