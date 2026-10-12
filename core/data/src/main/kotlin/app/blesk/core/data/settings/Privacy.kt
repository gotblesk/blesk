package app.blesk.core.data.settings

/** How long the app may stay in the background before it asks for the PIN again. */
enum class LockDelay(val millis: Long) {
    Immediately(0),
    OneMinute(60_000),
    FifteenMinutes(15 * 60_000),
    OneHour(60 * 60_000),
}

data class Privacy(
    val lockDelay: LockDelay = LockDelay.Immediately,
    val biometric: Boolean = false,
    val secureScreen: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicator: Boolean = true,
)

data class Profile(
    val name: String,
    val username: String,
    val bio: String,
    val photoVersion: Long,
    /** The header: "light:<preset>", "solid:<tone>" or "photo". */
    val header: String = DEFAULT_HEADER,
    val headerVersion: Long = 0,
) {
    val isEmpty: Boolean get() = name.isBlank() && username.isBlank()

    companion object {
        const val MAX_NAME = 64
        const val MAX_BIO = 160
        const val DEFAULT_HEADER = "light:0"
        const val MAX_USERNAME = 32

        /** Usernames are lowercase latin letters, digits and underscores, 3 to 32 characters. */
        fun normalizeUsername(raw: String): String =
            raw.trim().removePrefix("@").lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(MAX_USERNAME)

        fun isValidUsername(username: String): Boolean = username.length >= 3 && username == normalizeUsername(username)
    }
}
