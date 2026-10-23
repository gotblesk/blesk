package app.blesk.core.design.component

import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import app.blesk.core.design.R
import java.util.Locale

/**
 * How long is left to wait, for «Попробуй через …» and «Подожди …»: seconds under a minute,
 * otherwise whole minutes, both rounded up, so the wait never reads shorter than it is.
 */
@Composable
fun formatWait(millis: Long): String {
    val russian = rememberRussianResources()
    val seconds = ceilDiv(millis, MILLIS_PER_SECOND).coerceAtLeast(1)
    return if (seconds < SECONDS_PER_MINUTE) {
        russian.getQuantityString(R.plurals.design_wait_seconds, seconds.toInt(), seconds.toInt())
    } else {
        val minutes = ceilDiv(seconds, SECONDS_PER_MINUTE).toInt()
        russian.getQuantityString(R.plurals.design_wait_minutes, minutes, minutes)
    }
}

/**
 * The app's resources with Russian rules for numbers. The texts are Russian whatever the phone's
 * language, but plural forms follow the phone's: in English «5» would take the form of «2».
 */
@Composable
private fun rememberRussianResources(): Resources {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    return remember(context, configuration) {
        val russian = Configuration(configuration).apply { setLocale(Locale.forLanguageTag("ru")) }
        context.createConfigurationContext(russian).resources
    }
}

private fun ceilDiv(value: Long, by: Long): Long = (value + by - 1) / by

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
