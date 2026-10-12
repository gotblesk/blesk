package app.blesk.core.data.settings

/** «Сервер и прокси»: where the app's connections go first. Login and password are not kept. */
data class ProxySetting(
    val enabled: Boolean = false,
    val socks: Boolean = true,
    val host: String = "",
    val port: Int = 0,
) {
    val usable: Boolean get() = enabled && host.isNotBlank() && port in 1..MAX_PORT

    companion object {
        const val MAX_PORT = 65_535
    }
}
