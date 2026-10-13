package app.blesk.feature.debug

import android.content.ClipData
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blesk.core.data.diagnostics.CrashReport
import app.blesk.core.data.diagnostics.LogEntry
import app.blesk.core.design.component.Button
import app.blesk.core.design.component.ButtonKind
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.SectionHeader
import app.blesk.core.design.component.Text
import app.blesk.core.design.component.ToggleRow
import app.blesk.core.design.icon.BleskIcons
import app.blesk.core.design.theme.Blesk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("dd.MM HH:mm:ss.SSS")

/** The journal keeps more than this; the screen shows the most recent part. */
private const val JOURNAL_ROWS = 200

private fun format(millis: Long): String = timeFormat.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))

@Composable
fun DebugScreen(
    entries: ImmutableList<LogEntry>,
    crash: CrashReport?,
    appInfo: String,
    onClearCrash: () -> Unit,
    onClearJournal: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenMotion: () -> Unit = {},
    frameMeter: Boolean = false,
    onFrameMeter: (Boolean) -> Unit = {},
) {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }
    val mono = Blesk.type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
    val pad = Modifier.padding(horizontal = Blesk.dimens.gutter)

    ScrollScreen(title = stringResource(R.string.debug_title), onBack = onBack, modifier = modifier) {
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter).padding(bottom = 12.dp)) {
            ListRow(
                title = stringResource(R.string.motion_title),
                subtitle = stringResource(R.string.motion_open_hint),
                icon = BleskIcons.Spark,
                onClick = onOpenMotion,
            )
            ToggleRow(
                title = stringResource(R.string.frame_meter),
                subtitle = stringResource(R.string.frame_meter_hint),
                checked = frameMeter,
                onCheckedChange = onFrameMeter,
            )
        }
        Text(
            text = stringResource(R.string.debug_privacy),
            style = Blesk.type.label,
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp),
        )

        SectionHeader(stringResource(R.string.debug_crash))
        Card(pad) {
            if (crash == null) {
                Text(
                    text = stringResource(R.string.debug_no_crash),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                val header = stringResource(R.string.debug_crash_at, format(crash.atMillis))
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(header, style = Blesk.type.bodyStrong)
                    Text(
                        text = crash.trace,
                        style = mono,
                        color = Blesk.colors.textSecondary,
                        maxLines = 40,
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            text = stringResource(if (copied) R.string.debug_copied else R.string.debug_copy),
                            icon = if (copied) BleskIcons.Check else BleskIcons.Copy,
                            onClick = {
                                val report = "$appInfo\n$header\n${crash.trace}"
                                scope.launch {
                                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("blesk crash", report)))
                                    copied = true
                                }
                            },
                        )
                        Button(text = stringResource(R.string.debug_clear), kind = ButtonKind.Secondary, onClick = onClearCrash)
                    }
                }
            }
        }

        SectionHeader(stringResource(R.string.debug_journal))
        Card(pad) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.debug_journal_empty),
                    color = Blesk.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
            } else {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    entries.asReversed().take(JOURNAL_ROWS).forEach { entry ->
                        val value = entry.value?.let { " $it" }.orEmpty()
                        Text("${format(entry.atMillis)}  ${entry.code.name}$value", style = mono, color = Blesk.colors.textSecondary)
                    }
                }
            }
        }
        Button(
            text = stringResource(R.string.debug_clear),
            kind = ButtonKind.Secondary,
            onClick = onClearJournal,
            modifier = pad.padding(top = 12.dp),
        )
    }
}
