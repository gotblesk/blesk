package app.blesk

import app.blesk.core.data.settings.VibrationLevel
import app.blesk.core.motion.HapticLevel

internal fun VibrationLevel.toHapticLevel(): HapticLevel = when (this) {
    VibrationLevel.Off -> HapticLevel.Off
    VibrationLevel.Quiet -> HapticLevel.Quiet
    VibrationLevel.Normal -> HapticLevel.Normal
}
