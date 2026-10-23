package app.blesk.core.design.component

import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameProblem
import app.blesk.shared.protocol.UsernameVerdict
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UsernameStatusTest {

    private val offered = listOf("anya27", "anya.m")

    @Test
    fun everyVerdictHasItsStatus() {
        assertEquals(UsernameStatus.Free("anya"), statusOf("anya", UsernameCheck(true, UsernameVerdict.FREE, "anya")))
        assertEquals(
            UsernameStatus.Taken("anya", persistentListOf("anya27", "anya.m")),
            statusOf("anya", UsernameCheck(false, UsernameVerdict.TAKEN, "anya", offered)),
        )
        assertEquals(
            UsernameStatus.Reserved("adm1n", persistentListOf()),
            statusOf("adm1n", UsernameCheck(false, UsernameVerdict.RESERVED, "adm1n")),
        )
        assertEquals(UsernameStatus.Unknown, statusOf("anya", null))
    }

    @Test
    fun aNameTheServerFindsInvalidSaysWhichRuleItBreaks() {
        val invalid = UsernameCheck(false, UsernameVerdict.INVALID, null)
        assertEquals(UsernameStatus.Problem(UsernameProblem.EdgeSeparator), statusOf("anya_", invalid))
        assertEquals(UsernameStatus.Reserved("egor_bot", persistentListOf()), statusOf("egor_bot", invalid))
        // A rule newer than the app's: the name is kept back all the same.
        assertEquals(UsernameStatus.Reserved("anya", persistentListOf()), statusOf("anya", invalid))
    }

    @Test
    fun anOldServerOnlySaysFreeOrTaken() {
        assertEquals(UsernameStatus.Free("anya"), statusOf("anya", UsernameCheck(available = true)))
        assertEquals(UsernameStatus.Taken("anya", persistentListOf()), statusOf("anya", UsernameCheck(available = false)))
    }

    @Test
    fun aRefusalVibratesOncePerName() {
        val taken = UsernameStatus.Taken("anya", persistentListOf())
        assertTrue(refuseOn(taken, lastRefused = null))
        assertFalse(refuseOn(taken, lastRefused = "anya"))
        assertTrue(refuseOn(taken, lastRefused = "egor"))
        assertTrue(refuseOn(UsernameStatus.Reserved("admin", persistentListOf()), lastRefused = "anya"))
        assertFalse(refuseOn(UsernameStatus.Free("anya1"), lastRefused = null))
        assertFalse(refuseOn(UsernameStatus.Problem(UsernameProblem.TooShort), lastRefused = null))
        assertFalse(refuseOn(UsernameStatus.Unknown, lastRefused = null))
    }

    @Test
    fun anAnswerIsKeptForAMinute() {
        var now = 0L
        val cache = UsernameCache { now }
        val taken = UsernameCheck(false, UsernameVerdict.TAKEN, "anya")
        cache.put("anya", taken)
        now = 59_999
        assertEquals(taken, cache["anya"])
        now = 60_000
        assertNull(cache["anya"])
    }

    @Test
    fun namesTheServerOffersAreKeptAsFree() {
        val cache = UsernameCache { 0L }
        cache.put("anya", UsernameCheck(false, UsernameVerdict.TAKEN, "anya", offered))
        assertEquals(UsernameStatus.Free("anya27"), statusOf("anya27", cache["anya27"]))
        assertEquals(UsernameStatus.Free("anya.m"), statusOf("anya.m", cache["anya.m"]))
    }

    @Test
    fun theLeastRecentlyUsedNameGoesFirst() {
        val cache = UsernameCache { 0L }
        repeat(32) { cache.put("name$it", UsernameCheck(available = true)) }
        assertTrue(cache["name0"] != null)
        cache.put("name32", UsernameCheck(available = true))
        assertNull(cache["name1"])
        assertTrue(cache["name0"] != null)
        assertTrue(cache["name32"] != null)
    }

    @Test
    fun aRuleBrokenWhileTypingIsShownAfterThePauseWithoutAsking() = runTest {
        val asked = mutableListOf<String>()
        val checks = UsernameChecks(own = null, cache = UsernameCache { 0L })
        launch { checks.follow("an") { asked += it; null } }
        runCurrent()
        assertEquals(UsernameStatus.Checking, checks.status)
        advanceTimeBy(401)
        assertEquals(UsernameStatus.Problem(UsernameProblem.TooShort), checks.status)
        launch { checks.follow("admin") { asked += it; null } }
        advanceTimeBy(401)
        assertEquals(UsernameStatus.Reserved("admin", persistentListOf()), checks.status)
        assertEquals(emptyList<String>(), asked)
    }

    @Test
    fun theOwnNameIsYoursWithoutAsking() = runTest {
        val checks = UsernameChecks(own = "egor", cache = UsernameCache { 0L })
        launch { checks.follow("egor") { error("asked about $it") } }
        runCurrent()
        assertEquals(UsernameStatus.Yours, checks.status)
    }

    @Test
    fun aRefusalLearntAtSignUpShowsAndStays() = runTest {
        val asked = mutableListOf<String>()
        val checks = UsernameChecks(own = null, cache = UsernameCache { 0L })
        launch { checks.follow("anya") { asked += it; null } }
        advanceTimeBy(401)
        assertEquals(UsernameStatus.Unknown, checks.status)

        checks.learn("anya", UsernameCheck(false, UsernameVerdict.TAKEN, "anya"))
        assertEquals(UsernameStatus.Taken("anya", persistentListOf()), checks.status)
        checks.learn("egor", UsernameCheck(true, UsernameVerdict.FREE, "egor"))
        assertEquals(UsernameStatus.Taken("anya", persistentListOf()), checks.status)

        launch { checks.follow("egor") { asked += it; null } }
        runCurrent()
        assertEquals(UsernameStatus.Free("egor"), checks.status)
        assertEquals(listOf("anya"), asked)
    }
}
