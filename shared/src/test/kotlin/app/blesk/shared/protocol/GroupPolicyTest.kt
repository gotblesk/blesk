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
    fun aRoleManagerGivesNoRightTheyLack() {
        fun save(role: RoomRole) =
            GroupPolicy.apply(ranked, "kira", GroupChange.SaveRole(role), NOW)?.roles?.firstOrNull { it.id == role.id }
        val fans = ranked.roles.first { it.id == "fans" }
        assertNull(save(fans.copy(rights = fans.rights + Right.AssignRoles)))
        assertNull(save(RoomRole("new", "Новички", rights = setOf(Right.Ban))))
        // Her own role and those above are not hers to change at all.
        assertNull(save(ranked.roles.first { it.id == "keepers" }.copy(rights = setOf(Right.Roles, Right.Pin))))
        assertNull(save(ranked.roles.first { it.id == "mods" }.copy(title = "Модерация")))

        // What she has she may give below her; what a role had it keeps, or loses.
        assertEquals(setOf(Right.Timeout, Right.Pin), save(fans.copy(rights = fans.rights + Right.Pin))?.rights)
        assertEquals(setOf(Right.Timeout), save(fans.copy(title = "Друзья"))?.rights)
        assertEquals(emptySet<Right>(), save(fans.copy(rights = emptySet()))?.rights)
        assertEquals(setOf(Right.Roles), save(RoomRole("new", "Новички", rights = setOf(Right.Roles)))?.rights)
    }

    @Test
    fun rolesMoveOnlyBelowTheirMover() {
        fun move(id: String, to: Int) = GroupPolicy.apply(ranked, "kira", GroupChange.MoveRole(id, to), NOW)?.roles?.map { it.id }
        assertNull(move("guests", 0))
        assertNull(move("guests", 1))
        assertNull(move("keepers", 0))
        assertNull(move("mods", 3))
        assertNull(GroupPolicy.apply(ranked, "kira", GroupChange.DeleteRole("mods"), NOW))
        assertEquals(listOf("mods", "keepers", "guests", "fans"), move("guests", 2))
        assertNotNull(GroupPolicy.apply(ranked, "kira", GroupChange.DeleteRole("guests"), NOW))
    }

    @Test
    fun aRoleIsGivenOnlyBelowTheGiverAndOnlyWithWhatTheyHave() {
        val assigning = ranked.role("keepers") { it.copy(rights = it.rights + Right.AssignRoles) }
        fun give(state: GroupState, roleId: String, to: String = "anya") = GroupPolicy
            .apply(state, "kira", GroupChange.AssignRole(to, roleId, on = true), NOW)
            ?.members?.first { it.username == to }?.roles
        assertNull(give(assigning, "mods"))
        assertNull(give(assigning, "keepers"))
        assertNull(give(assigning, "guests", to = "kira"))
        // The fans may time people out, which she may not; the guests see the closed channel, which she does not.
        assertNull(give(assigning, "fans"))
        assertNull(give(assigning, "guests"))
        assertEquals(listOf("guests"), give(assigning.channel("c2") { it.copy(hiddenFrom = it.hiddenFrom + "guests") }, "guests"))
        assertEquals(listOf("fans"), give(assigning.role("keepers") { it.copy(rights = it.rights + Right.Timeout) }, "fans"))
    }

    @Test
    fun onlyAnAdminMakesAdminsAndNeverOfThemselves() {
        // Kira may add admins through her role, yet every admin would stand above her: she makes none, herself least.
        val appointing = ranked.role("keepers") { it.copy(rights = it.rights + Right.AddAdmins) }
        assertNull(GroupPolicy.apply(appointing, "kira", GroupChange.Admin("kira", emptySet()), NOW))
        assertNull(GroupPolicy.apply(appointing, "kira", GroupChange.Admin("anya", emptySet()), NOW))

        fun lead(change: GroupChange.Admin) =
            GroupPolicy.apply(ranked, "lead", change, NOW)?.members?.first { it.username == change.username }
        assertEquals(setOf(Right.Ban, Right.Pin), lead(GroupChange.Admin("anya", setOf(Right.Ban, Right.Pin)))?.rights)
        assertNull(lead(GroupChange.Admin("anya", setOf(Right.Roles))))
        assertNull(lead(GroupChange.Admin("lead", setOf(Right.AddAdmins, Right.Ban, Right.Pin))))
        assertEquals(setOf(Right.Ban), lead(GroupChange.Admin("lead", setOf(Right.Ban)))?.rights)
        assertEquals(GroupRole.Member, lead(GroupChange.Admin("lead", null))?.role)
    }

    @Test
    fun anAdminLeavesTheOwnerAndOtherAdminsAlone() {
        fun lead(change: GroupChange) = GroupPolicy.apply(ranked, "lead", change, NOW)
        assertNull(lead(GroupChange.Admin("admin", setOf(Right.Ban))))
        assertNull(lead(GroupChange.Admin("admin", null)))
        assertNull(lead(GroupChange.Admin("owner", setOf(Right.Ban))))
        assertNull(lead(GroupChange.Remove("admin")))
        assertNull(lead(GroupChange.Ban("owner")))
        assertNotNull(lead(GroupChange.Remove("kira")))
    }

    @Test
    fun nobodyRaisesTheirOwnRights() {
        // Here everyone may change the details, and so what every member may do, but only within what they may do.
        val open = ranked.copy(memberRights = ranked.memberRights + Right.EditInfo)
        fun set(actor: String, rights: Set<Right>) = GroupPolicy.apply(open, actor, GroupChange.MemberRights(rights), NOW)?.memberRights
        assertNull(set("anya", open.memberRights + Right.Ban))
        assertNull(set("kira", open.memberRights + Right.AssignRoles))
        assertNull(set("lead", open.memberRights + Right.Roles))
        assertEquals(open.memberRights + Right.Roles, set("kira", open.memberRights + Right.Roles))
        assertEquals(open.memberRights - Right.Media, set("anya", open.memberRights - Right.Media))
    }

    @Test
    fun aStateFarAheadIsTakenOnlyFromTheOwner() {
        // The guard may ban. Her ban sent as a version no later change of the owner's could pass would stop the room.
        val banned = applied(GroupPolicy.apply(guarded, "guard", GroupChange.Ban("anya"), NOW))
        val far = banned.copy(version = 2_147_483_647)
        assertFalse(GroupPolicy.admits(guarded, far, "guard"))
        assertTrue(GroupPolicy.admits(guarded, far, "owner"))
        val step = GroupPolicy.MAX_VERSION_STEP
        assertFalse(GroupPolicy.admits(guarded, banned.copy(version = guarded.version + step + 1), "guard"))
        // A phone that missed a few states still takes the next one.
        assertTrue(GroupPolicy.admits(guarded, banned.copy(version = guarded.version + step), "guard"))
    }

    @Test
    fun aStateRemovesAndStripsOnlyWhomItsSenderMay() {
        fun GroupState.without(username: String) = copy(members = members.filter { it.username != username })
        fun changes(base: GroupState) = listOf(
            base.without("admin"),
            base.without("mod"),
            base.without("anya"),
            base.member("mod") { it.copy(roles = emptyList()) },
            base.member("finn") { it.copy(roles = emptyList()) },
            base.copy(roles = base.roles.filter { it.id != "fans" }).member("finn") { it.copy(roles = emptyList()) },
        ).map { it.copy(version = base.version + 1) }

        // Eve's role may only arrange channels: she removes no one, takes no role off anyone, deletes no role.
        changes(helped).forEach { incoming ->
            assertFalse("eve: $incoming", GroupPolicy.admits(helped, incoming, "eve"))
            assertTrue("owner: $incoming", GroupPolicy.admits(helped, incoming, "owner"))
        }
        // Given the right to ban and to give roles, she removes and strips those below her, and still not those above.
        val able = helped.role("helpers") { it.copy(rights = setOf(Right.Ban, Right.AssignRoles)) }
        val allowed = changes(able).map { GroupPolicy.admits(able, it, "eve") }
        assertEquals(listOf(false, false, true, false, true, false), allowed)
    }

    @Test
    fun aChannelIsDeletedOnlyByOneWhoSeesIt() {
        val shut = helped.channel("c2") { it.copy(hiddenFrom = it.hiddenFrom + "helpers") }
        fun without(id: String) = shut.copy(version = shut.version + 1, channels = shut.channels.filter { it.id != id })
        // Gone, then back under its id and open to her, it would be hers to read, and the moderators would not know.
        assertFalse(GroupPolicy.admits(shut, without("c2"), "eve"))
        assertTrue(GroupPolicy.admits(shut, without("c2"), "owner"))
        assertTrue(GroupPolicy.admits(shut, without("c1"), "eve"))
    }

    @Test
    fun aStateChangesOnlyWhatItsSenderMay() {
        // Here members may not invite, so neither may Eve.
        val base = helped.copy(
            memberRights = helped.memberRights - Right.Invite,
            banned = listOf("mallory"),
            invites = listOf(GroupInvite("abcdefgh", "Друзья")),
            log = listOf(LogEntry(NOW, "owner", LogAction.Banned, "mallory")),
        )
        val changes = listOf(
            // The admin put first, and so made the keeper of the closed voice channels' keys in the owner's place.
            base.copy(members = base.members.sortedByDescending { it.username == "admin" }),
            base.copy(members = base.members + GroupMember("sock", GroupRole.Member)),
            base.copy(banned = emptyList()),
            base.copy(invites = base.invites + GroupInvite("ijklmnop", "Свои")),
            base.copy(invite = "ijklmnop"),
            base.copy(title = "Чужой"),
            base.copy(description = "о другом"),
            base.copy(hue = 3),
            base.copy(memberRights = base.memberRights - Right.Media),
            base.role("fans") { it.copy(title = "Друзья") },
            base.copy(log = emptyList()),
            base.copy(log = listOf(LogEntry(NOW, "owner", LogAction.Edited))),
        ).map { it.copy(version = base.version + 1) }
        changes.forEach { incoming ->
            assertFalse("eve: $incoming", GroupPolicy.admits(base, incoming, "eve"))
            assertTrue("owner: $incoming", GroupPolicy.admits(base, incoming, "owner"))
        }

        // Channels are hers to arrange, and what she does goes into the journal; they are not the guard's.
        val arranged = listOf(
            base.channel("c1") { it.copy(title = "главное", kind = GroupChannelKind.Voice, adminsOnly = true) },
            base.copy(channels = base.channels.reversed()),
            applied(GroupPolicy.apply(base, "eve", GroupChange.RenameChannel("c1", "главное"), NOW)),
        ).map { it.copy(version = base.version + 1) }
        arranged.forEach { assertTrue("eve: $it", GroupPolicy.admits(base, it, "eve")) }
        val guarding = base.copy(members = base.members + GroupMember("guard", GroupRole.Member, roles = listOf("guards")))
            .copy(roles = base.roles + RoomRole("guards", "Охрана", rights = setOf(Right.Ban)))
        arranged.dropLast(1).map { it.copy(members = guarding.members, roles = guarding.roles) }.forEach {
            assertFalse("guard: $it", GroupPolicy.admits(guarding, it, "guard"))
        }
    }

    @Test
    fun theJournalOnlyGrowsByWhatWasDone() {
        val full = helped.copy(log = List(GroupPolicy.MAX_LOG) { LogEntry(it.toLong(), "owner", LogAction.Edited) })
        val once = applied(GroupPolicy.apply(full, "eve", GroupChange.RenameChannel("c1", "главное"), NOW))
        assertEquals(full.log.drop(1), once.log.dropLast(1))
        assertTrue(GroupPolicy.admits(full, once, "eve"))
        // Two lines in one change are a line made up, one pushing out a line she would rather no one saw.
        val twice = once.copy(log = once.log.drop(1) + LogEntry(NOW, "owner", LogAction.Edited))
        assertFalse(GroupPolicy.admits(full, twice, "eve"))
        assertTrue(GroupPolicy.admits(full, twice.copy(version = full.version + 2), "eve"))
        assertFalse(GroupPolicy.admits(full, once.copy(log = once.log.dropLast(2) + once.log.last()), "eve"))
    }

    @Test
    fun anAdminKeepsWhatMembersLoseAndMayGiveItBack() {
        val editing = room.member("admin") { it.copy(rights = it.rights.orEmpty() + Right.EditInfo) }
        for (right in listOf(Right.Write, Right.Media, Right.Speak)) {
            val off = applied(GroupPolicy.apply(editing, "admin", GroupChange.MemberRights(editing.memberRights - right), NOW))
            assertTrue("$right", GroupPolicy.can(off, "admin", right))
            assertFalse("$right", GroupPolicy.can(off, "anya", right))
            val on = GroupPolicy.apply(off, "admin", GroupChange.MemberRights(off.memberRights + right), NOW)
            assertEquals("$right", editing.memberRights, on?.memberRights)
        }
    }

    @Test
    fun anAdminGivesTheVoiceAndMentionRightsARoleCarries() {
        // On this stage members may not speak; the owner's speakers may, and the hosts may mute, move and mention all.
        val stage = room.copy(
            memberRights = room.memberRights - Right.Speak,
            roles = room.roles + RoomRole("speakers", "Спикеры", rights = setOf(Right.Speak)) +
                RoomRole("hosts", "Ведущие", rights = setOf(Right.MuteOthers, Right.Move, Right.MentionAll)),
        )
        for (role in listOf("speakers", "hosts")) {
            val given = GroupPolicy.apply(stage, "admin", GroupChange.AssignRole("anya", role, on = true), NOW)
            assertEquals(role, listOf(role), given?.members?.first { it.username == "anya" }?.roles)
        }
        val lead = stage.member("admin") { it.copy(rights = null) }
        val voice = RoomRole("voice", "Голос", rights = setOf(Right.Speak, Right.MuteOthers, Right.Move, Right.MentionAll))
        assertEquals(voice.rights, GroupPolicy.apply(lead, "admin", GroupChange.SaveRole(voice), NOW)?.roles?.last()?.rights)
        // What admins hold whatever members may stops at the rights an admin is given: those still come only from above.
        assertFalse(GroupPolicy.can(stage, "admin", Right.Roles))
        assertNull(GroupPolicy.apply(stage, "admin", GroupChange.SaveRole(voice), NOW))
    }

    @Test
    fun theOwnerMayDoAllOfIt() {
        val everything = Right.entries.toSet()
        listOf(
            GroupChange.SaveRole(RoomRole("keepers", "Хранители", rights = everything)),
            GroupChange.SaveRole(RoomRole("new", "Новички", rights = everything)),
            GroupChange.MoveRole("guests", 0),
            GroupChange.DeleteRole("mods"),
            GroupChange.AssignRole("anya", "mods", on = true),
            GroupChange.AssignRole("kira", "fans", on = true),
            GroupChange.Admin("admin", everything),
            GroupChange.Admin("lead", null),
            GroupChange.Admin("kira", everything),
            GroupChange.MemberRights(everything),
            GroupChange.Remove("admin"),
        ).forEach { change ->
            val next = applied(GroupPolicy.apply(ranked, "owner", change, NOW))
            assertTrue("$change", GroupPolicy.admits(ranked, next, "owner"))
        }
    }

    @Test
    fun aStateThatRaisesItsSenderIsRefused() {
        // Kira's role may change roles, give them, change the details and add admins.
        val able = ranked.role("keepers") { it.copy(rights = setOf(Right.Roles, Right.AssignRoles, Right.EditInfo, Right.AddAdmins)) }
        val keepers = able.roles.first { it.id == "keepers" }
        val raised = listOf(
            "kira" to able.role("keepers") { it.copy(rights = it.rights + Right.Ban) },
            "kira" to able.copy(roles = listOf(keepers) + able.roles.filter { it != keepers }),
            "kira" to able.copy(roles = listOf(RoomRole("new", "Новички")) + able.roles),
            "kira" to able.copy(roles = able.roles.filter { it != keepers }),
            "kira" to able.role("fans") { it.copy(rights = it.rights + Right.Ban) },
            "kira" to able.copy(roles = able.roles + RoomRole("new", "Новички", rights = setOf(Right.Ban))),
            "kira" to able.member("anya") { it.copy(roles = listOf("guests")) },
            "kira" to able.member("anya") { it.copy(roles = listOf("fans")) },
            "kira" to able.copy(memberRights = able.memberRights + Right.Ban),
            "kira" to able.member("kira") { it.copy(role = GroupRole.Admin, rights = emptySet()) },
            "kira" to able.member("anya") { it.copy(role = GroupRole.Admin, rights = emptySet()) },
            "lead" to able.member("lead") { it.copy(rights = it.rights.orEmpty() + Right.Roles) },
            "lead" to able.member("admin") { it.copy(rights = setOf(Right.Ban)) },
            "lead" to able.member("admin") { it.copy(role = GroupRole.Member, rights = null) },
            "lead" to able.member("anya") { it.copy(role = GroupRole.Admin, rights = setOf(Right.Roles)) },
            "lead" to able.member("lead") { it.copy(role = GroupRole.Owner, rights = null) },
        ).map { (sender, state) -> sender to state.copy(version = able.version + 1) }

        raised.forEach { (sender, incoming) ->
            assertFalse("$sender: $incoming", GroupPolicy.admits(able, incoming, sender))
            assertTrue("owner: $incoming", GroupPolicy.admits(able, incoming, "owner"))
        }
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
        val byKira = listOf(
            GroupChange.SaveRole(RoomRole("guests", "Гости", rights = setOf(Right.Pin))),
            GroupChange.SaveRole(RoomRole("newer", "Новенькие", rights = setOf(Right.Roles))),
            GroupChange.MoveRole("guests", 2),
            GroupChange.DeleteRole("guests"),
        )
        val byLead = listOf(
            GroupChange.Admin("anya", setOf(Right.Ban)),
            GroupChange.Admin("lead", setOf(Right.Ban)),
            GroupChange.Admin("lead", null),
        )
        byKira.forEach { assertNotNull("kira: $it", GroupPolicy.apply(ranked, "kira", it, NOW)) }
        byLead.forEach { assertNotNull("lead: $it", GroupPolicy.apply(ranked, "lead", it, NOW)) }
        val tries = listOf(guarded to changes, ranked to changes + byKira + byLead).flatMap { (state, list) ->
            state.members.flatMap { member -> list.map { Triple(state, member.username, it) } }
        }
        tries.forEach { (state, actor, change) ->
            GroupPolicy.apply(state, actor, change, NOW)?.let { next ->
                assertTrue("$actor: $change", GroupPolicy.admits(state, next, actor))
            }
        }
    }

    /** [room] with a member whose only right is to ban, who does not see the closed channel. */
    private val guarded = room.copy(
        members = room.members + GroupMember("guard", GroupRole.Member, roles = listOf("guards")),
        channels = room.channels.map { if (it.id == "c2") it.copy(hiddenFrom = it.hiddenFrom + "guards") else it },
        roles = room.roles + RoomRole("guards", "Охрана", rights = setOf(Right.Ban)),
    )

    /**
     * [room] with Kira, whose keepers' role may change roles and nothing more: it ranks under the moderators and above
     * the fans, who may time people out, and the guests. The closed channel is shut to the keepers and the fans, open to
     * the moderators and the guests. Lead is an admin who may add admins and ban, no more.
     */
    private val ranked = room.copy(
        members = room.members + GroupMember("kira", GroupRole.Member, roles = listOf("keepers")) +
            GroupMember("lead", GroupRole.Admin, rights = setOf(Right.AddAdmins, Right.Ban)),
        channels = room.channels.map { if (it.id == "c2") it.copy(hiddenFrom = it.hiddenFrom + setOf("keepers", "fans")) else it },
        roles = listOf(
            room.roles.first { it.id == "mods" },
            RoomRole("keepers", "Хранители", rights = setOf(Right.Roles)),
            RoomRole("fans", "Фанаты", rights = setOf(Right.Timeout)),
            RoomRole("guests", "Гости"),
        ),
    )

    /** [room] with Eve, whose helpers' role may only arrange channels, between the moderators and the fans; Finn is a fan. */
    private val helped = room.copy(
        members = room.members + GroupMember("eve", GroupRole.Member, roles = listOf("helpers")) +
            GroupMember("finn", GroupRole.Member, roles = listOf("fans")),
        roles = listOf(room.roles[0], RoomRole("helpers", "Помощники", rights = setOf(Right.Channels)), room.roles[1]),
    )

    private fun GroupState.role(id: String, change: (RoomRole) -> RoomRole) =
        copy(roles = roles.map { if (it.id == id) change(it) else it })

    private fun GroupState.member(username: String, change: (GroupMember) -> GroupMember) =
        copy(members = members.map { if (it.username == username) change(it) else it })

    private fun GroupState.channel(id: String, change: (GroupChannel) -> GroupChannel) =
        copy(channels = channels.map { if (it.id == id) change(it) else it })

    private fun applied(state: GroupState?): GroupState = requireNotNull(state)

    private companion object {
        const val NOW = 1_000_000L
    }
}
