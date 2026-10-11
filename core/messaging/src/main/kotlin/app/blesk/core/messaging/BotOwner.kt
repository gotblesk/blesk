package app.blesk.core.messaging

import app.blesk.core.data.BotAccounts
import app.blesk.core.data.BotResult
import app.blesk.core.data.BotWithToken
import app.blesk.core.network.ApiException
import app.blesk.core.network.BleskApi
import app.blesk.shared.protocol.BotInfo
import app.blesk.shared.protocol.Bots
import app.blesk.shared.protocol.CreateBot
import java.io.IOException

/** Your bots on the server; the token is only ever shown, never stored here. */
internal class BotOwner(private val api: BleskApi) : BotAccounts {
    override suspend fun myBots(): List<BotInfo> = runCatching { api.myBots() }.getOrDefault(emptyList())

    override suspend fun createBot(username: String, name: String, about: String): Pair<BotResult, BotWithToken?> {
        val handle = username.trim().lowercase().removePrefix("@").let { if (it.endsWith(Bots.SUFFIX)) it else it + Bots.SUFFIX }
        if (!Bots.isValidUsername(handle)) return BotResult.NameInvalid to null
        return try {
            val created = api.createBot(CreateBot(handle, name.trim().take(MAX_BOT_NAME), about.trim()))
            BotResult.Ok to BotWithToken(created.bot, created.token)
        } catch (e: ApiException) {
            when {
                e.code == "too_many_bots" -> BotResult.TooMany
                e.status == CONFLICT -> BotResult.NameTaken
                e.status in CLIENT_ERRORS && e.status != TOO_MANY -> BotResult.NameInvalid
                else -> BotResult.Failed
            } to null
        } catch (_: IOException) {
            BotResult.Failed to null
        }
    }

    override suspend fun renewToken(username: String): BotWithToken? =
        runCatching { api.renewBotToken(username).let { BotWithToken(it.bot, it.token) } }.getOrNull()

    override suspend fun deleteBot(username: String): Boolean = runCatching { api.deleteBot(username) }.isSuccess

    private companion object {
        const val CONFLICT = 409
        const val TOO_MANY = 429
        const val MAX_BOT_NAME = 64
        val CLIENT_ERRORS = 400..499
    }
}
