package app.blesk.detekt

import io.gitlab.arturbosch.detekt.test.lint
import org.junit.Assert.assertEquals
import org.junit.Test

class ClickWithoutResponseTest {
    @Test
    fun reportsAClickableWithoutIndication() {
        val code = """
            fun Modifier.dead() = clickable(interactionSource = source, indication = null) {}
        """.trimIndent()
        assertEquals(1, ClickWithoutResponse().lint(code).size)
    }

    @Test
    fun acceptsTheAppIndication() {
        val code = """
            fun Modifier.alive() = clickable(role = Role.Button) {}
        """.trimIndent()
        assertEquals(0, ClickWithoutResponse().lint(code).size)
    }
}
