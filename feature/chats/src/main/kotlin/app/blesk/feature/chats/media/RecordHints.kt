package app.blesk.feature.chats.media

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * «Отпусти — отправить · влево — отмена · вверх — закрепить» while holding, for the first few
 * recordings only; then the gestures are known.
 */
internal class RecordHints(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val showing: Boolean get() = prefs.getInt(KEY, 0) < TIMES

    fun recorded() {
        prefs.edit().putInt(KEY, prefs.getInt(KEY, 0) + 1).apply()
    }

    private companion object {
        const val PREFS = "recording"
        const val KEY = "hinted"

        // Counted as a recording starts, so the first three show it.
        const val TIMES = 4
    }
}

@Composable
internal fun rememberRecordHints(): RecordHints {
    val context = LocalContext.current
    return remember(context) { RecordHints(context.applicationContext) }
}
