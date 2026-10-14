package app.blesk.feature.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import java.util.Locale

/** «1 248»: thousands split by a thin space, as Russian typography wants. */
fun groupedCount(count: Int): String = String.format(Locale.ROOT, "%,d", count).replace(',', THIN_SPACE)

/** «1,2 тыс.», «12 тыс.», «1,3 млн»: short enough for a list row; small numbers stay whole. */
@Composable
fun shortCount(count: Int): String = when {
    count < THOUSAND -> count.toString()
    count < MILLION -> stringResource(R.string.count_thousands, oneDecimal(count, THOUSAND))
    else -> stringResource(R.string.count_millions, oneDecimal(count, MILLION))
}

/** A channel's subscribers in a list: whole below a thousand, short above. */
@Composable
fun subscribersLine(count: Int): String =
    if (count < THOUSAND) {
        pluralStringResource(R.plurals.chats_subscribers, count, count)
    } else {
        // Past a thousand the word is always in the genitive plural: «1,2 тыс. подписчиков».
        stringResource(R.string.count_subscribers_short, shortCount(count))
    }

private fun oneDecimal(count: Int, unit: Int): String {
    val whole = count / unit
    val tenth = count % unit * TEN / unit
    return if (whole >= TEN || tenth == 0) whole.toString() else "$whole,$tenth"
}

private const val THIN_SPACE = ' '
private const val THOUSAND = 1_000
private const val MILLION = 1_000_000
private const val TEN = 10
