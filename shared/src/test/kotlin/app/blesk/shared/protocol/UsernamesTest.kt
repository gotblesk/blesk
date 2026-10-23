package app.blesk.shared.protocol

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UsernamesTest {
    @Test
    fun `capitals become small as they are typed`() {
        assertEquals(TypedUsername("egor", droppedNonLatin = false, cut = false), Usernames.typed("Egor"))
        assertEquals(TypedUsername("egor", droppedNonLatin = false, cut = false), Usernames.typed("@Egor"))
        assertEquals(TypedUsername("ilya", droppedNonLatin = false, cut = false), Usernames.typed("İlya"))
    }

    @Test
    fun `direction marks, zero-width characters, blank letters and zalgo vanish without a word`() {
        listOf("‮eg​or⁦", "é̂̃gor", "ㅤegor").forEach {
            assertEquals(it, TypedUsername("egor", droppedNonLatin = false, cut = false), Usernames.typed(it))
        }
    }

    @Test
    fun `wide, bold and accented latin letters become plain ones`() {
        listOf("Ｅｇｏｒ", "𝐞𝐠𝐨𝐫", "égor").forEach { assertEquals(it, "egor", Usernames.typed(it).text) }
    }

    @Test
    fun `cyrillic is dropped and said so`() {
        assertEquals(TypedUsername("", droppedNonLatin = true, cut = false), Usernames.typed("егор"))
        assertEquals(TypedUsername("gor", droppedNonLatin = true, cut = false), Usernames.typed("еgor"))
    }

    @Test
    fun `a profile link gives its name`() {
        assertEquals("egor.m", Usernames.typed("https://blesk.su/@Egor.M?k=abc").text)
        assertEquals("egor", Usernames.typed("blesk.su/@egor").text)
    }

    @Test
    fun `a name is cut at 32 and says so`() {
        val typed = Usernames.typed("a".repeat(40))
        assertEquals("a".repeat(Usernames.MAX), typed.text)
        assertTrue(typed.cut)
        assertFalse(Usernames.typed("a".repeat(Usernames.MAX)).cut)
    }

    @Test
    fun `every broken rule is named`() {
        mapOf(
            "Egor" to UsernameProblem.Characters,
            "ab" to UsernameProblem.TooShort,
            "a".repeat(Usernames.MAX + 1) to UsernameProblem.TooLong,
            "9egor" to UsernameProblem.StartsWithNonLetter,
            "_egor" to UsernameProblem.EdgeSeparator,
            "egor_" to UsernameProblem.EdgeSeparator,
            "eg..or" to UsernameProblem.DoubleSeparator,
            "eg._or" to UsernameProblem.DoubleSeparator,
            "egor_bot" to UsernameProblem.BotName,
            "blesk" to UsernameProblem.Reserved,
            "egor.makarov" to null,
        ).forEach { (name, problem) -> assertEquals(name, problem, Usernames.problem(name)) }
    }

    @Test
    fun `look-alikes of the service are reserved`() {
        listOf(
            "blesk", "b1esk", "bl3sk", "b1e5k", "b.l.e.s.k", "myblesk", "blesk_support", "support", "support24",
            "support_24", "admin_team", "adm1n", "r00t", "moderator7", "official.news", "system1", "help2024", "bot", "all",
            "bl_esk", "my.blesk", "suppor7", "officia1", "r007", "inf0", "a11", "lega1", "mai1", "adrnin", "systern", "rnoderator",
        ).forEach { assertTrue(it, Usernames.isReserved(it)) }
    }

    @Test
    fun `ordinary names are not reserved`() {
        listOf(
            "egor", "lesya", "blessing", "alice", "milena", "admiral", "helpful", "teamlead", "bob", "smoke_1700000000",
            "ali", "ali1990", "ali_95", "mali", "gleb.leskov", "gleb_leskova", "debbie.skinner", "noble_sky", "zombie_skull",
        ).forEach { assertFalse(it, Usernames.isReserved(it)) }
    }

    @Test
    fun `names taken under older rules stay well formed`() {
        assertTrue(Usernames.isWellFormed("support1"))
        assertFalse(Usernames.isValid("support1"))
    }

    @Test
    fun `a name has no problem exactly when it is well formed, not reserved and not a bot's`() {
        val random = Random(1)
        val alphabet = "abz019._E-"
        repeat(5_000) {
            val name = String(CharArray(random.nextInt(0, 41)) { alphabet[random.nextInt(alphabet.length)] })
            val expected = Usernames.isWellFormed(name) && !Usernames.isReserved(name) && !name.endsWith(Bots.SUFFIX)
            assertEquals(name, expected, Usernames.problem(name) == null)
        }
    }

    @Test
    fun `variants are valid, distinct, short enough and never the name itself`() {
        val bases = listOf("egor", "egor.makarov", "a", "ab", "egor_bot", "9egor", "x".repeat(40), "egor27")
        for (seed in 0 until 50) {
            for (base in bases) {
                val variants = Usernames.variants(base, Random(seed))
                assertTrue("$base/$seed", variants.isNotEmpty() && variants.size <= Usernames.VARIANTS)
                assertEquals("$base/$seed", variants, variants.distinct())
                variants.forEach { name ->
                    assertNull("$base/$seed: $name", Usernames.problem(name))
                    assertTrue("$base/$seed: $name", name.length <= Usernames.MAX)
                    assertTrue("$base/$seed: $name", name != base)
                }
            }
        }
    }

    @Test
    fun `variants repeat with the same seed`() {
        assertEquals(Usernames.variants("egor", Random(42)), Usernames.variants("egor", Random(42)))
    }

    @Test
    fun `variants of a service name are none`() {
        assertEquals(emptyList<String>(), Usernames.variants("blesk", Random(0)))
        assertEquals(emptyList<String>(), Usernames.variants("support24", Random(0)))
    }

    @Test
    fun `variants offer the name without digits, parts joined and swapped`() {
        assertEquals("egor", Usernames.variants("egor27", Random(0)).first())
        val parts = Usernames.variants("egor.makarov", Random(0))
        assertTrue(parts.toString(), "egormakarov" in parts && "makarov.egor" in parts)
    }

    @Test
    fun `normalize keeps what typing keeps`() {
        assertEquals("egor", Usernames.normalize("@Egor"))
    }

    @Test
    fun `an older server's check still reads`() {
        val check = Json.decodeFromString<UsernameCheck>("""{"available":false}""")
        assertNull(check.verdict)
        assertNull(check.username)
        assertEquals(emptyList<String>(), check.suggestions)
    }
}
