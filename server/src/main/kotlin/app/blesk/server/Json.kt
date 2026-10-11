package app.blesk.server

import kotlinx.serialization.json.Json

/** Default class discriminator ("type") on purpose: the client decodes frames with the stock Json settings. */
val BleskJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
