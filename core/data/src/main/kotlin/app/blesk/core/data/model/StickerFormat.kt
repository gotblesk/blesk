package app.blesk.core.data.model

import kotlinx.serialization.Serializable

/** What a sticker file is: a still WebP or PNG, an animated WebP, or a Lottie animation. */
@Serializable
enum class StickerFormat(val extension: String, val mime: String) {
    Static("webp", "image/webp"),
    Animated("webp", "image/webp"),
    Lottie("json", "application/json"),
}
