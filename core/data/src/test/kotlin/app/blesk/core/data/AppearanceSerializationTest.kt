package app.blesk.core.data

import app.blesk.shared.appearance.Appearance
import app.blesk.shared.appearance.ThemeSpec
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSerializationTest {

    @Test
    fun `appearance survives a round trip and is normalised`() {
        val original = Appearance(theme = ThemeSpec.Lime, glassStrength = 0.4f, textScale = 1.1f)
        val json = Json.encodeToString(Appearance.serializer(), original)
        assertEquals(original, Json.decodeFromString(Appearance.serializer(), json))
        assertEquals(1f, Appearance(glassStrength = 7f).normalized().glassStrength)
    }
}
