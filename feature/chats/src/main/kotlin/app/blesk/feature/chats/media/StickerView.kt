package app.blesk.feature.chats.media

import android.content.Context
import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import app.blesk.core.data.model.StickerFormat
import app.blesk.core.design.theme.Blesk
import coil3.compose.AsyncImage
import coil3.gif.AnimatedImageDecoder
import coil3.gif.repeatCount
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import java.io.File

/**
 * A sticker on a transparent background. An animated one plays [plays] times when it comes on
 * screen and then rests on its last frame; a new [replay] value plays it again. Without motion or
 * in battery saving it stays still.
 */
@Composable
fun StickerView(
    path: String,
    format: StickerFormat,
    modifier: Modifier = Modifier,
    description: String? = null,
    plays: Int = PLAYS_IN_CHAT,
    replay: Int = 0,
) {
    val context = LocalContext.current
    val still = Blesk.motion.reduced || !Blesk.motion.autoplay || remember { context.powerSaving() }
    val described = if (description != null) Modifier.semantics { contentDescription = description } else Modifier
    Box(modifier.then(described)) {
        if (path.isEmpty()) return@Box
        when (format) {
            StickerFormat.Lottie -> key(replay) { LottieSticker(path, if (still) 0 else plays) }
            StickerFormat.Static -> AsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            StickerFormat.Animated -> key(replay) {
                val request = remember(path, still) {
                    val animate = !still && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ImageRequest.Builder(context)
                        .data(File(path))
                        .apply { if (animate) decoderFactory(AnimatedImageDecoder.Factory()) }
                        .repeatCount(plays - 1)
                        // A cached drawable would come back already played out; each replay decodes afresh.
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun LottieSticker(path: String, plays: Int) {
    val composition by rememberLottieComposition(LottieCompositionSpec.File(path))
    val progress by animateLottieCompositionAsState(composition, iterations = plays.coerceAtLeast(1), isPlaying = plays > 0)
    LottieAnimation(composition = composition, progress = { if (plays > 0) progress else 1f }, modifier = Modifier.fillMaxSize())
}

private fun Context.powerSaving(): Boolean = getSystemService(PowerManager::class.java)?.isPowerSaveMode == true

/** How many times an animated sticker plays in the chat before it rests. */
const val PLAYS_IN_CHAT = 3
