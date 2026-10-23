package app.blesk.server

import app.blesk.server.auth.UsernameChecks
import app.blesk.shared.protocol.UsernameCheck
import app.blesk.shared.protocol.UsernameVerdict
import app.blesk.shared.protocol.Usernames
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UsernameChecksTest {
    /** Stands in for the accounts table and counts the questions put to it. */
    private class Accounts(private val names: Set<String> = emptySet()) {
        val questions = mutableListOf<Collection<String>>()

        suspend fun taken(asked: Collection<String>): Set<String> {
            questions += asked
            return asked.filterTo(HashSet()) { it in names }
        }
    }

    @Test
    fun `a name past 64 characters is invalid without asking the database`() = runBlocking {
        val accounts = Accounts()
        val checks = UsernameChecks(accounts::taken)
        assertEquals(UsernameCheck(available = false, verdict = UsernameVerdict.INVALID), checks.check("a".repeat(65)))
        assertEquals(0, accounts.questions.size)
        assertEquals(UsernameVerdict.INVALID, checks.check("a".repeat(64)).verdict)
        assertEquals(1, accounts.questions.size)
    }

    @Test
    fun `a free name costs one question and comes back canonical`() = runBlocking {
        val accounts = Accounts()
        val check = UsernameChecks(accounts::taken).check("Egor")
        assertEquals(UsernameCheck(available = true, verdict = UsernameVerdict.FREE, username = "egor"), check)
        assertEquals(1, accounts.questions.size)
        assertTrue("egor" in accounts.questions.single())
    }

    @Test
    fun `a taken name gets the first three free variants from the same question`() = runBlocking {
        val variants = Usernames.variants("egor", Random(SEED))
        val accounts = Accounts(setOf("egor") + variants.take(2))
        val check = UsernameChecks(accounts::taken, Random(SEED)).check("egor")
        assertEquals(UsernameCheck(false, UsernameVerdict.TAKEN, "egor", variants.drop(2).take(UsernameChecks.SUGGESTIONS)), check)
        assertEquals(1, accounts.questions.size)
    }

    @Test
    fun `a bot or service name is reserved`() = runBlocking {
        val accounts = Accounts()
        val checks = UsernameChecks(accounts::taken)
        val bot = checks.check("egor_bot")
        assertEquals(UsernameVerdict.RESERVED, bot.verdict)
        assertEquals(UsernameChecks.SUGGESTIONS, bot.suggestions.size)
        assertTrue(bot.suggestions.toString(), bot.suggestions.all(Usernames::isValid))
        assertEquals(UsernameCheck(false, UsernameVerdict.RESERVED, "blesk"), checks.check("blesk"))
        // Every variant of a service name is one too, so there is nothing to ask about.
        assertEquals(1, accounts.questions.size)
    }

    @Test
    fun `a broken name is invalid`() = runBlocking {
        val check = UsernameChecks(Accounts()::taken).check("9egor")
        assertEquals(false, check.available)
        assertEquals(UsernameVerdict.INVALID, check.verdict)
        assertEquals("9egor", check.username)
    }

    private companion object {
        const val SEED = 7
    }
}
