package app.blesk.server

import app.blesk.shared.protocol.CreateSpaceChannel
import app.blesk.shared.protocol.ErrorBody
import app.blesk.shared.protocol.RoleChange
import app.blesk.shared.protocol.SpaceChannel
import app.blesk.shared.protocol.SpaceInfo
import app.blesk.shared.protocol.SpaceKind
import app.blesk.shared.protocol.SpaceMember
import app.blesk.shared.protocol.SpaceRole
import app.blesk.shared.protocol.Spaces
import app.blesk.shared.protocol.UpdateSpace
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacesTest {
    @Test
    fun `creating a space makes the creator its owner with one default channel`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val alice = harness.register()
        val channel = spaces.create(alice, SpaceKind.Channel)
        assertEquals(SpaceRole.Owner, channel.role)
        assertEquals(1, channel.members)
        assertEquals(listOf(SpaceChannel("main", channel.title)), channel.channels)

        val room = spaces.create(alice, SpaceKind.Room)
        assertEquals(listOf(SpaceChannel("general", "общее")), room.channels)

        val taken = spaces.createRaw(alice, channel.handle, SpaceKind.Room)
        assertEquals(HttpStatusCode.Conflict, taken.status)
        assertEquals("handle_taken", taken.body<ErrorBody>().code)
        assertEquals("invalid_handle", spaces.createRaw(alice, "Bad Handle", SpaceKind.Room).body<ErrorBody>().code)
        assertEquals("invalid_handle", spaces.createRaw(alice, "mine", SpaceKind.Room).body<ErrorBody>().code)
    }

    @Test
    fun `search, join, mine and leave`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val alice = harness.register()
        val bob = harness.register()
        val marker = randomUsername().takeLast(8)
        val small = spaces.create(alice, SpaceKind.Room, "small_$marker")
        val big = spaces.createRaw(alice, "big_$marker", SpaceKind.Channel, title = "Kotlin ${marker.uppercase()} news").body<SpaceInfo>()

        val joined = spaces.join(bob, big.handle).body<SpaceInfo>()
        assertEquals(SpaceRole.Member, joined.role)
        assertEquals(2, joined.members)
        assertEquals(HttpStatusCode.OK, spaces.join(bob, big.handle).status)
        assertEquals(2, spaces.info(bob, big.handle).members)

        val found = harness.client.get("${Spaces.PATH}?query=$marker") { bearerAuth(bob.token) }.body<List<SpaceInfo>>()
        assertEquals(listOf(big.handle, small.handle), found.map { it.handle })
        val byPrefix = harness.client.get("${Spaces.PATH}?query=small_$marker") { bearerAuth(bob.token) }.body<List<SpaceInfo>>()
        assertEquals(listOf(small.handle), byPrefix.map { it.handle })

        val mine = harness.client.get("${Spaces.PATH}/mine") { bearerAuth(bob.token) }.body<List<SpaceInfo>>()
        assertEquals(listOf(big.handle), mine.map { it.handle })

        assertEquals(HttpStatusCode.NoContent, spaces.leave(bob, big.handle).status)
        assertNull(spaces.info(bob, big.handle).role)
        val ownerLeaves = spaces.leave(alice, big.handle)
        assertEquals(HttpStatusCode.Conflict, ownerLeaves.status)
        assertEquals("owner_cannot_leave", ownerLeaves.body<ErrorBody>().code)
    }

    @Test
    fun `only owner and admins edit, only the owner deletes`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val alice = harness.register()
        val bob = harness.register()
        val space = spaces.create(alice, SpaceKind.Room)
        spaces.join(bob, space.handle)

        assertEquals(HttpStatusCode.Forbidden, update(harness, bob, space.handle, UpdateSpace(title = "Mine now")).status)
        val updated = update(harness, alice, space.handle, UpdateSpace(about = "About")).body<SpaceInfo>()
        assertEquals("About", updated.about)
        assertEquals(space.title, updated.title)

        assertEquals(HttpStatusCode.Forbidden, harness.client.delete("${Spaces.PATH}/${space.handle}") { bearerAuth(bob.token) }.status)
        assertEquals(HttpStatusCode.NoContent, harness.client.delete("${Spaces.PATH}/${space.handle}") { bearerAuth(alice.token) }.status)
        assertEquals(HttpStatusCode.NotFound, harness.client.get("${Spaces.PATH}/${space.handle}") { bearerAuth(alice.token) }.status)
    }

    @Test
    fun `roles follow rank and members see the member list`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val admin = harness.register()
        val member = harness.register()
        val other = harness.register()
        val space = spaces.create(owner, SpaceKind.Room)
        listOf(admin, member, other).forEach { spaces.join(it, space.handle) }

        assertEquals(HttpStatusCode.Forbidden, role(harness, member, space.handle, RoleChange(other.username, SpaceRole.Admin)).status)
        assertEquals(HttpStatusCode.NoContent, role(harness, owner, space.handle, RoleChange(admin.username, SpaceRole.Admin)).status)
        assertEquals("invalid_role", role(harness, owner, space.handle, RoleChange(admin.username, SpaceRole.Owner)).body<ErrorBody>().code)
        // An admin may not demote the owner or another admin.
        assertEquals(HttpStatusCode.Forbidden, role(harness, admin, space.handle, RoleChange(owner.username, SpaceRole.Member)).status)
        assertEquals(HttpStatusCode.NoContent, role(harness, owner, space.handle, RoleChange(other.username, SpaceRole.Admin)).status)
        assertEquals(HttpStatusCode.Forbidden, role(harness, admin, space.handle, RoleChange(other.username, SpaceRole.Member)).status)

        val members = harness.client.get("${Spaces.PATH}/${space.handle}/members") { bearerAuth(member.token) }.body<List<SpaceMember>>()
        assertEquals(SpaceMember(owner.username, SpaceRole.Owner), members.first())
        assertEquals(setOf(admin.username, other.username), members.filter { it.role == SpaceRole.Admin }.map { it.username }.toSet())
        assertEquals(4, members.size)
        val page = harness.client.get("${Spaces.PATH}/${space.handle}/members?offset=1&limit=2") { bearerAuth(member.token) }
        assertEquals(members.subList(1, 3), page.body<List<SpaceMember>>())

        val outsider = harness.register()
        val hidden = harness.client.get("${Spaces.PATH}/${space.handle}/members") { bearerAuth(outsider.token) }
        assertEquals(HttpStatusCode.Forbidden, hidden.status)
        assertEquals("not_member", hidden.body<ErrorBody>().code)
    }

    @Test
    fun `bans remove membership and stop rejoining until lifted`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val admin = harness.register()
        val member = harness.register()
        val otherAdmin = harness.register()
        val space = spaces.create(owner, SpaceKind.Room)
        listOf(admin, member, otherAdmin).forEach { spaces.join(it, space.handle) }
        role(harness, owner, space.handle, RoleChange(admin.username, SpaceRole.Admin))
        role(harness, owner, space.handle, RoleChange(otherAdmin.username, SpaceRole.Admin))

        assertEquals(HttpStatusCode.Forbidden, spaces.ban(member, space.handle, admin.username).status)
        assertEquals(HttpStatusCode.Forbidden, spaces.ban(admin, space.handle, owner.username).status)
        assertEquals(HttpStatusCode.Forbidden, spaces.ban(admin, space.handle, otherAdmin.username).status)

        assertEquals(HttpStatusCode.NoContent, spaces.ban(admin, space.handle, member.username).status)
        assertNull(spaces.info(member, space.handle).role)
        val rejoin = spaces.join(member, space.handle)
        assertEquals(HttpStatusCode.Forbidden, rejoin.status)
        assertEquals("banned", rejoin.body<ErrorBody>().code)

        assertEquals(HttpStatusCode.NoContent, spaces.unban(admin, space.handle, member.username).status)
        assertEquals(HttpStatusCode.OK, spaces.join(member, space.handle).status)

        assertEquals(HttpStatusCode.NoContent, spaces.ban(owner, space.handle, otherAdmin.username).status)
    }

    @Test
    fun `rooms manage text channels within limits`() = serverTest { harness ->
        val spaces = SpaceClient(harness)
        val owner = harness.register()
        val member = harness.register()
        val room = spaces.create(owner, SpaceKind.Room)
        val channel = spaces.create(owner, SpaceKind.Channel)
        spaces.join(member, room.handle)

        assertEquals("not_a_room", addChannel(harness, owner, channel.handle, "extra").body<ErrorBody>().code)
        assertEquals(HttpStatusCode.Forbidden, addChannel(harness, member, room.handle, "extra").status)

        var info = room
        repeat(Spaces.MAX_CHANNELS - 1) { info = addChannel(harness, owner, room.handle, "c$it").body() }
        assertEquals(Spaces.MAX_CHANNELS, info.channels.size)
        assertTrue(info.channels.drop(1).all { Regex("^[a-z0-9]{8}$").matches(it.id) })
        val tooMany = addChannel(harness, owner, room.handle, "one more")
        assertEquals(HttpStatusCode.Conflict, tooMany.status)
        assertEquals("too_many_channels", tooMany.body<ErrorBody>().code)

        val removable = info.channels.last().id
        assertEquals(HttpStatusCode.NoContent, removeChannel(harness, owner, room.handle, removable).status)
        assertEquals(HttpStatusCode.NotFound, removeChannel(harness, owner, room.handle, removable).status)

        val last = removeChannel(harness, owner, channel.handle, "main")
        assertEquals(HttpStatusCode.Conflict, last.status)
        assertEquals("last_channel", last.body<ErrorBody>().code)
    }

    private suspend fun update(harness: Harness, user: TestUser, handle: String, update: UpdateSpace): HttpResponse =
        harness.client.patch("${Spaces.PATH}/$handle") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(update)
        }

    private suspend fun role(harness: Harness, user: TestUser, handle: String, change: RoleChange): HttpResponse =
        harness.client.post("${Spaces.PATH}/$handle/roles") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(change)
        }

    private suspend fun addChannel(harness: Harness, user: TestUser, handle: String, title: String): HttpResponse =
        harness.client.post("${Spaces.PATH}/$handle/channels") {
            bearerAuth(user.token)
            contentType(ContentType.Application.Json)
            setBody(CreateSpaceChannel(title))
        }

    private suspend fun removeChannel(harness: Harness, user: TestUser, handle: String, id: String): HttpResponse =
        harness.client.delete("${Spaces.PATH}/$handle/channels/$id") { bearerAuth(user.token) }
}
