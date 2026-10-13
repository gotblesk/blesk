package app.blesk.navigation

import android.content.Context
import android.os.Build
import android.provider.Settings

/**
 * The name a phone is known by, «Nothing Phone (3a) Pro» rather than its model code A059P: first
 * from the list of codes shipped in the app, then the name the system gives the phone, then the
 * maker and the code. Reads a file, so it is called off the main thread.
 */
internal fun deviceName(context: Context, model: String = Build.MODEL, maker: String = Build.MANUFACTURER): String {
    val known = runCatching {
        context.assets.open(DEVICES).bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") && '=' in it }
                .map { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
                .filter { (code, _) -> model.startsWith(code, ignoreCase = true) }
                .maxByOrNull { (code, _) -> code.length }
                ?.second
        }
    }.getOrNull()
    if (known != null) return known
    val system = runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
    if (!system.isNullOrBlank() && !system.equals(model, ignoreCase = true)) return system
    val brand = maker.replaceFirstChar { it.uppercase() }
    return if (model.startsWith(maker, ignoreCase = true)) model else "$brand $model"
}

private const val DEVICES = "devices.txt"
