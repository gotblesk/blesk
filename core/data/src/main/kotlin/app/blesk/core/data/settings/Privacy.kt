package app.blesk.core.data.settings

/** How long the app may stay in the background before it asks for the PIN again. */
enum class LockDelay(val millis: Long) {
    Immediately(0),
    OneMinute(60_000),
    FiveMinutes(5 * 60_000),
    OneHour(60 * 60_000),
}

/** Who may do something with you: anyone, only your contacts, or nobody. */
enum class Audience { Everyone, Contacts, Nobody }

data class Privacy(
    val lockDelay: LockDelay = LockDelay.Immediately,
    val biometric: Boolean = false,
    val secureScreen: Boolean = false,
    val readReceipts: Boolean = true,
    val typingIndicator: Boolean = true,
    /** Your phone opens the first link of a message you send and attaches its card. */
    val linkPreviews: Boolean = true,
    /** Whether typing your username finds you; with it off, only your link or QR code does. */
    val findable: Boolean = true,
    /** Who may write to you; from anyone else messages are dropped on this phone. Never [Audience.Nobody]. */
    val write: Audience = Audience.Everyone,
    /** Who may ring you; anyone else's call never rings. */
    val calls: Audience = Audience.Everyone,
    /** Who may add you to groups and rooms. */
    val groups: Audience = Audience.Everyone,
    /** Who sees when you are online. */
    val online: Audience = Audience.Contacts,
    /** Photos and videos can be saved from the viewer to the phone's gallery. */
    val saveMedia: Boolean = true,
    /** Who gets your profile photo along with your name. */
    val photo: Audience = Audience.Contacts,
    /** Ten wrong codes in a row erase everything on this phone. */
    val wipeAfterTen: Boolean = false,
    /** Keyboards are asked not to learn from what you type here. */
    val incognitoKeyboard: Boolean = true,
    /** Disappearing messages for chats you start; 0 keeps messages. One of [TIMER_STEPS]. */
    val defaultTimerSeconds: Int = 0,
) {
    companion object {
        /** Off, an hour, a day, a week: the same steps as in each chat. */
        val TIMER_STEPS = listOf(0, 3_600, 86_400, 604_800)
    }
}

data class Profile(
    val name: String,
    val username: String,
    val bio: String,
    val photoVersion: Long,
    /** The header: "light:<preset>", "solid:<tone>" or "photo". */
    val header: String = DEFAULT_HEADER,
    val headerVersion: Long = 0,
    /** Up to [MAX_LINKS] web addresses shown under the bio. */
    val links: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = name.isBlank() && username.isBlank()

    companion object {
        const val MAX_NAME = 64
        const val MAX_BIO = 160
        const val MAX_LINKS = 3
        const val MAX_LINK = 200

        /** Trimmed, non-empty, distinct and no more than [MAX_LINKS] of them. */
        fun cleanLinks(links: List<String>): List<String> =
            links.map { it.trim().take(MAX_LINK) }.filter { it.isNotEmpty() }.distinct().take(MAX_LINKS)
        const val DEFAULT_HEADER = "light:0"
        const val MAX_USERNAME = 32

        /** Usernames are lowercase latin letters, digits and underscores, 3 to 32 characters. */
        fun normalizeUsername(raw: String): String =
            raw.trim().removePrefix("@").lowercase().filter { it in 'a'..'z' || it in '0'..'9' || it == '_' }.take(MAX_USERNAME)

        fun isValidUsername(username: String): Boolean = username.length >= 3 && username == normalizeUsername(username)
    }
}
