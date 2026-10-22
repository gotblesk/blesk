package app.blesk.core.design.theme

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Instant

/**
 * Where screens take «now» from when they show or arrange things by the current time: message
 * times, «сегодня», who is online. The app reads the system clock; tests stop it at one moment.
 */
val LocalClock = staticCompositionLocalOf<() -> Instant> { Instant::now }
