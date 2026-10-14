package app.blesk.shared.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val next = applied(GroupPolicy.apply(room, "owner", GroupChange.Describe(title = "Клуб 2", description = "о книгах"), NOW))
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

    private fun applied(state: GroupState?): GroupState = requireNotNull(state)

    private companion object {
        const val NOW = 1_000_000L
    }
}
