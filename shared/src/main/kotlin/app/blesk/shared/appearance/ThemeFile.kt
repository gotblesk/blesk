package app.blesk.shared.appearance

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** A theme saved to a file or sent to a chat. Only colours travel; nothing about the sender does. */
@Serializable
data class ThemeFile(
    @SerialName("blesk_theme") val version: Int = VERSION,
    val theme: ThemeSpec,
) {
    fun encode(): String = json.encodeToString(serializer(), copy(theme = theme.normalized()))

    companion object {
        const val VERSION = 1
        const val EXTENSION = "blesktheme"
        const val MIME = "application/json"
        private const val MAX_BYTES = 4 * 1024

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
        }

        /** Parses a theme from untrusted input; anything malformed, oversized or from the future is rejected. */
        fun decode(raw: String): ThemeFile? {
            if (raw.length > MAX_BYTES) return null
            return try {
                json.decodeFromString(serializer(), raw)
                    .takeIf { it.version in 1..VERSION }
                    ?.let { it.copy(theme = it.theme.copy(preset = ThemePreset.Custom).normalized()) }
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    }
}
