package app.blesk.feature.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.blesk.core.design.component.Card
import app.blesk.core.design.component.ListRow
import app.blesk.core.design.component.ScrollScreen
import app.blesk.core.design.component.Text
import app.blesk.core.design.theme.Blesk

/** One third-party component and the license it comes under. */
data class LicensedComponent(val name: String, val author: String, val license: License)

enum class License(val title: String, @param:RawRes val text: Int) {
    Agpl3("GNU AGPL 3.0", R.raw.license_agpl3),
    Apache2("Apache License 2.0", R.raw.license_apache2),
    Ofl("SIL Open Font License 1.1", R.raw.license_ofl),
    Unicode("Unicode License v3", R.raw.license_unicode),
    SqlCipher("SQLCipher License (BSD)", R.raw.license_sqlcipher),
}

/** Everything shipped inside the APK. Keep in sync with the dependencies. */
val ShippedComponents = listOf(
    LicensedComponent("blesk", "blesk", License.Agpl3),
    LicensedComponent("Onest", "The Onest Project Authors", License.Ofl),
    LicensedComponent("libsignal", "Signal Messenger", License.Agpl3),
    LicensedComponent("SQLCipher", "Zetetic", License.SqlCipher),
    LicensedComponent("Ktor", "JetBrains", License.Apache2),
    LicensedComponent("Coil", "Coil Contributors", License.Apache2),
    LicensedComponent("Jetpack Compose, AndroidX", "The Android Open Source Project", License.Apache2),
    LicensedComponent("Kotlin, kotlinx.coroutines, kotlinx.serialization", "JetBrains", License.Apache2),
    LicensedComponent("Backdrop", "Kyant", License.Apache2),
    LicensedComponent("ZXing", "ZXing authors", License.Apache2),
    LicensedComponent("LiveKit Android", "LiveKit", License.Apache2),
    LicensedComponent("AudioSwitch", "Twilio, David Liu", License.Apache2),
    LicensedComponent("Emoji2 Emoji Picker (data)", "The Android Open Source Project", License.Apache2),
    LicensedComponent("Unicode CLDR (emoji names)", "Unicode, Inc.", License.Unicode),
)

@Composable
fun LicensesScreen(onOpenLicense: (License) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    ScrollScreen(title = stringResource(R.string.licenses_title), onBack = onBack, modifier = modifier) {
        Text(
            text = stringResource(R.string.licenses_intro),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.padding(horizontal = Blesk.dimens.gutter + 4.dp).padding(bottom = 16.dp),
        )
        Card(Modifier.padding(horizontal = Blesk.dimens.gutter)) {
            ShippedComponents.forEach { component ->
                ListRow(
                    title = component.name,
                    subtitle = "${component.author} · ${component.license.title}",
                    onClick = { onOpenLicense(component.license) },
                )
            }
        }
    }
}

@Composable
fun LicenseTextScreen(license: License, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val text =
        remember(license) { context.resources.openRawResource(license.text).bufferedReader().use { it.readText() } }
    ScrollScreen(title = license.title, onBack = onBack, modifier = modifier) {
        Text(
            text = text,
            style = Blesk.type.caption.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp),
            color = Blesk.colors.textSecondary,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Blesk.dimens.gutter),
        )
    }
}
