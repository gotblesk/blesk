package app.blesk.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupPolicyTest {
    private val room = GroupState(
        id = "room-1",
        kind = GroupKind.Room,
        title = "Клуб",
        version = 1,
        epoch = 0,
        members = listOf(
            GroupMember("owner", GroupRole.Owner),
            GroupMember("admin", GroupRole.Admin, rights = setOf(Right.Ban, Right.Invite, Right.AssignRoles)),
            GroupMember("mod", GroupRole.Member, roles = listOf("mods")),
            GroupMember("anya", GroupRole.Member),
        ),
        channels = listOf(GroupChannel("c1", "общее"), GroupChannel("c2", "модерация", hiddenFrom = setOf(GroupPolicy.EVERYONE))),
        roles = listOf(RoomRole("mods", "Модераторы", rights = setOf(Right.DeleteOthers)), RoomRole("fans", "Фанаты")),
    )

    @Test
    fun membersCannotEditTheRoom() {
        assertNull(GroupPolicy.apply(room, "anya", GroupChange.Describe(title = "Чужой"), NOW))
    }

    @Test
    fun ownerEditsAndTheJournalRemembers() {
        val change = GroupChange.Describe(title = "Клуб 2", description = "о книгах")
        val next = applied(GroupPolicy.apply(room, "owner", change, NOW))
        assertEquals("Клуб 2", next.title)
        assertEquals(room.version + 1, next.version)
        assertEquals(LogAction.Edited, next.log.single().action)
    }

    @Test
    fun adminCannotGiveRightsTheyLack() {
        assertNull(GroupPolicy.apply(room, "admin", GroupChange.Admin("anya", setOf(Right.Ban)), NOW))
        assertNull(GroupPolicy.apply(room, "owner", GroupChange.Admin("owner", setOf(Right.Ban)), NOW))
        val next = GroupPolicy.apply(room, "owner", GroupChange.Admin("anya", setOf(Right.Pin)), NOW)
        assertEquals(GroupRole.Admin, next?.roleOf("anya"))
    }

    @Test
    fun banRemovesAndRotatesKeys() {
        val next = applied(GroupPolicy.apply(room, "admin", GroupChange.Ban("anya"), NOW))
        assertNull(next.roleOf("anya"))
        assertTrue("anya" in next.banned)
        assertEquals(room.epoch + 1, next.epoch)
        assertNull(GroupPolicy.apply(room, "admin", GroupChange.Ban("owner"), NOW))
    }

    @Test
    fun transferMakesTheOldOwnerAnAdmin() {
        assertNull(GroupPolicy.apply(room, "admin", GroupChange.TransferOwnership("anya"), NOW))
        val next = applied(GroupPolicy.apply(room, "owner", GroupChange.TransferOwnership("anya"), NOW))
        assertEquals(GroupRole.Owner, next.roleOf("anya"))
        assertEquals(GroupRole.Admin, next.roleOf("owner"))
    }

    @Test
    fun onlyTheOwnerDissolves() {
        assertNull(GroupPolicy.apply(room, "admin", GroupChange.Dissolve, NOW))
        assertEquals(listOf("owner"), GroupPolicy.apply(room, "owner", GroupChange.Dissolve, NOW)?.members?.map { it.username })
    }

    @Test
    fun rolesAddRightsAndHideChannels() {
        assertTrue(GroupPolicy.can(room, "mod", Right.DeleteOthers))
        assertFalse(GroupPolicy.can(room, "anya", Right.DeleteOthers))
        val hidden = room.channels[1]
        assertFalse(GroupPolicy.sees(room, "anya", hidden))
        assertTrue(GroupPolicy.sees(room, "mod", hidden))
        assertTrue(GroupPolicy.sees(room, "admin", hidden))
    }

    @Test
    fun rolesAreGivenOnlyDownward() {
        val given = GroupPolicy.apply(room, "admin", GroupChange.AssignRole("anya", "fans", on = true), NOW)
        assertEquals(listOf("fans"), given?.members?.first { it.username == "anya" }?.roles)
        assertNull(GroupPolicy.apply(room, "mod", GroupChange.AssignRole("anya", "fans", on = true), NOW))
    }

    @Test
    fun invitesRunOut() {
        val invite = GroupInvite("abcdefgh", "Друзья", expiresAt = NOW + 10, limit = 1)
        assertTrue(invite.usable(NOW))
        assertFalse(invite.usable(NOW + 10))
        assertFalse(invite.copy(joined = listOf("anya")).usable(NOW))
        val next = applied(GroupPolicy.apply(room, "admin", GroupChange.NewInvite(invite), NOW))
        val revoked = applied(GroupPolicy.apply(next, "admin", GroupChange.Revoke("abcdefgh"), NOW))
        assertFalse(revoked.invites.single().usable(NOW))
    }

    @Test
    fun channelsAndRolesReorder() {
        val moved = GroupPolicy.apply(room, "owner", GroupChange.MoveChannel("c2", 0), NOW)
        assertEquals(listOf("c2", "c1"), moved?.channels?.map { it.id })
        val roles = GroupPolicy.apply(room, "owner", GroupChange.MoveRole("fans", 0), NOW)
        assertEquals(listOf("fans", "mods"), roles?.roles?.map { it.id })
    }

    @Test
    fun readersOfAClosedChannelAreThoseWhoSeeIt() {
        assertEquals(listOf("owner", "admin", "mod"), GroupPolicy.readers(room, "room-1:c2"))
        val everyone = listOf("owner", "admin", "mod", "anya")
        assertEquals(everyone, GroupPolicy.readers(room, "room-1:c1"))
        assertEquals(everyone, GroupPolicy.readers(room, "room-1"))
        assertEquals(emptyList<String>(), GroupPolicy.readers(room, "room-1:gone"))
        assertFalse(GroupPolicy.reads(room, "stranger", "room-1"))
    }

    @Test
    fun onlyAChannelHiddenFromSomeoneHasAKeyOfItsOwn() {
        assertEquals("", GroupPolicy.keyScope(room, "room-1"))
        assertEquals("", GroupPolicy.keyScope(room, "room-1:c1"))
        assertEquals("c2", GroupPolicy.keyScope(room, "room-1:c2"))
        // Hidden from fans alone it is read by everyone, and still gets a key of its own: one key too many is harmless.
        val partly = room.copy(channels = room.channels + GroupChannel("c3", "без фанатов", hiddenFrom = setOf("fans")))
        assertEquals("c3", GroupPolicy.keyScope(partly, "room-1:c3"))
        assertEquals(listOf("owner", "admin", "mod", "anya"), GroupPolicy.readers(partly, "room-1:c3"))
    }

    @Test
    fun visibilityMovesWithRolesAndRights() {
        fun readersAfter(change: GroupChange, channel: String = "c2") =
            GroupPolicy.readers(applied(GroupPolicy.apply(room, "owner", change, NOW)), "room-1:$channel")
        assertTrue("anya" in readersAfter(GroupChange.AssignRole("anya", "mods", on = true)))
        assertFalse("mod" in readersAfter(GroupChange.AssignRole("mod", "mods", on = false)))
        assertFalse("mod" in readersAfter(GroupChange.DeleteRole("mods")))
        assertFalse("admin" in readersAfter(GroupChange.Admin("admin", null)))
        assertFalse("anya" in readersAfter(GroupChange.HideChannel("c1", setOf(GroupPolicy.EVERYONE)), channel = "c1"))
    }

    @Test
    fun aRoleThatDoesNotExistOpensNothing() {
        val ghost = room.member("anya") { it.copy(roles = listOf("ghost")) }
        assertFalse(GroupPolicy.sees(ghost, "anya", ghost.channels[1]))
        assertFalse(GroupPolicy.reads(ghost, "anya", "room-1:c2"))
    }

    @Test
    fun aNewRoleStaysOutOfClosedChannels() {
        val saved = applied(GroupPolicy.apply(room, "owner", GroupChange.SaveRole(RoomRole("new", "Новички")), NOW))
        assertEquals(setOf(GroupPolicy.EVERYONE, "new"), saved.channels[1].hiddenFrom)
        assertEquals(emptySet<String>(), saved.channels[0].hiddenFrom)
        val given = applied(GroupPolicy.apply(saved, "owner", GroupChange.AssignRole("anya", "new", on = true), NOW))
        assertFalse(GroupPolicy.sees(given, "anya", given.channels[1]))

        val edited = GroupChange.SaveRole(RoomRole("mods", "Модерация"))
        val renamed = applied(GroupPolicy.apply(room, "owner", edited, NOW))
        assertEquals(room.channels, renamed.channels)
        assertTrue(GroupPolicy.sees(renamed, "mod", renamed.channels[1]))
    }

    @Test
    fun onlyThoseWhoSeeAChannelMayReopenIt() {
        val everyoneArranges = room.copy(memberRights = room.memberRights + Right.Channels)
        assertNull(GroupPolicy.apply(everyoneArranges, "anya", GroupChange.HideChannel("c2", emptySet()), NOW))
        assertNull(GroupPolicy.apply(everyoneArranges, "anya", GroupChange.HideChannel("gone", emptySet()), NOW))
        val reopened = GroupPolicy.apply(everyoneArranges, "mod", GroupChange.HideChannel("c2", emptySet()), NOW)
        assertEquals(emptySet<String>(), reopened?.channels?.get(1)?.hiddenFrom)
    }

    @Test
    fun writingToAnUnknownChannelIsRefused() {
        assertFalse(GroupPolicy.canWrite(room, "owner", "room-1:zzz"))
        assertTrue(GroupPolicy.canWrite(room, "owner", "room-1"))
        assertTrue(GroupPolicy.canWrite(room, "mod", "room-1:c2"))
        assertFalse(GroupPolicy.canWrite(room, "anya", "room-1:c2"))
    }

    @Test
    fun aStateThatOpensAChannelIsTakenOnlyFromSomeoneWithTheRight() {
        val newcomers = RoomRole("new", "Новички")
        val opening = listOf(
            guarded.channel("c2") { it.copy(hiddenFrom = emptySet()) },
            guarded.member("guard") { it.copy(roles = it.roles + "mods") },
            guarded.copy(roles = guarded.roles + newcomers)
                .channel("c2") { it.copy(hiddenFrom = it.hiddenFrom + newcomers.id) }
                .member("anya") { it.copy(roles = listOf(newcomers.id)) },
            guarded.copy(roles = guarded.roles + newcomers),
            guarded.member("anya") { it.copy(role = GroupRole.Admin, rights = emptySet()) },
        ).map { it.copy(version = guarded.version + 1) }

        opening.forEach { incoming ->
            assertFalse(GroupPolicy.admitsVisibility(guarded, incoming, "guard"))
            assertTrue(GroupPolicy.admitsVisibility(guarded, incoming, "owner"))
        }
    }

    @Test
    fun narrowingNeedsNoRight() {
        val banned = applied(GroupPolicy.apply(guarded, "guard", GroupChange.Ban("anya"), NOW))
        val closed = banned.channel("c1") { it.copy(hiddenFrom = setOf(GroupPolicy.EVERYONE)) }
        assertTrue(GroupPolicy.admitsVisibility(guarded, closed, "guard"))
    }

    @Test
    fun everyHonestChangePassesTheGuard() {
        val invite = GroupInvite("abcdefgh", "Друзья")
        val changes = listOf(
            GroupChange.Describe(title = "Клуб 2"),
            GroupChange.MemberRights(Right.MEMBER_DEFAULT - Right.Media),
            GroupChange.Admin("anya", setOf(Right.Pin)),
            GroupChange.Admin("admin", null),
            GroupChange.Remove("anya"),
            GroupChange.Ban("anya"),
            GroupChange.Unban("someone"),
            GroupChange.NewInvite(invite),
            GroupChange.Revoke(invite.code),
            GroupChange.TransferOwnership("anya"),
            GroupChange.Dissolve,
            GroupChange.RenameChannel("c1", "главное"),
            GroupChange.MoveChannel("c2", 0),
            GroupChange.HideChannel("c2", emptySet()),
            GroupChange.HideChannel("c1", setOf(GroupPolicy.EVERYONE)),
            GroupChange.SaveRole(RoomRole("new", "Новички", rights = setOf(Right.Pin))),
            GroupChange.SaveRole(RoomRole("mods", "Модерация")),
            GroupChange.MoveRole("fans", 0),
            GroupChange.DeleteRole("mods"),
            GroupChange.AssignRole("anya", "mods", on = true),
            GroupChange.AssignRole("mod", "mods", on = false),
        )
        changes.forEach { assertNotNull("$it", GroupPolicy.apply(guarded, "owner", it, NOW)) }
        listOf("owner", "admin", "mod", "anya", "guard").forEach { actor ->
            changes.forEach { change ->
                GroupPolicy.apply(guarded, actor, change, NOW)?.let { next ->
                    assertTrue("$actor: $change", GroupPolicy.admitsVisibility(guarded, next, actor))
                }
            }
        }
    }

    /** [room] with a member whose only right is to ban, who does not see the closed channel. */
    private val guarded = room.copy(
        members = room.members + GroupMember("guard", GroupRole.Member, roles = listOf("guards")),
        channels = room.channels.map { if (it.id == "c2") it.copy(hiddenFrom = it.hiddenFrom + "guards") else it },
        roles = room.roles + RoomRole("guards", "Охрана", rights = setOf(Right.Ban)),
    )

    private fun GroupState.member(username: String, change: (GroupMember) -> GroupMember) =
        copy(members = members.map { if (it.username == username) change(it) else it })

    private fun GroupState.channel(id: String, change: (GroupChannel) -> GroupChannel) =
        copy(channels = channels.map { if (it.id == id) change(it) else it })

    private fun applied(state: GroupState?): GroupState = requireNotNull(state)

    private companion object {
        const val NOW = 1_000_000L
    }
}
