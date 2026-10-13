package app.blesk.economy

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import app.blesk.core.data.settings.Economy
import app.blesk.core.data.settings.EconomyEffects
import app.blesk.core.data.settings.EconomyMode
import app.blesk.core.data.settings.EconomyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The charge in percent, whether the phone is on its charger and whether system power saving is on. */
data class Battery(val level: Int, val charging: Boolean, val saver: Boolean)

/**
 * Decides when the economy mode is on. «Всегда» is always on; «Авто» turns on below the charge
 * threshold or with system power saving, and off on the charger or once the charge is 5 % above
 * the threshold again, so it does not flicker at the edge. With the mode off it offers itself once
 * per discharge when the charge drops below the threshold; «Авто» turning on by itself is told once.
 */
class EconomyMonitor(context: Context, private val scope: CoroutineScope, private val store: EconomyStore) {
    private val app = context.applicationContext
    private val battery = MutableStateFlow(readBattery(app))
    private val autoOn = MutableStateFlow(false)
    private var offered = false
    private var announced = false

    val economy: StateFlow<Economy> = store.economy.stateIn(scope, SharingStarted.Eagerly, Economy())

    val active: StateFlow<Boolean> = combine(economy, autoOn) { economy, auto ->
        when (economy.mode) {
            EconomyMode.Off -> false
            EconomyMode.Always -> true
            EconomyMode.Auto -> auto
        }
    }.stateIn(scope, SharingStarted.Eagerly, false)

    val effects: StateFlow<EconomyEffects> = combine(economy, active) { economy, active -> EconomyEffects.of(economy, active) }
        .stateIn(scope, SharingStarted.Eagerly, EconomyEffects.None)

    private val offerState = MutableStateFlow<Int?>(null)

    /** The charge to show in «Включить экономный режим?», while that capsule is up. */
    val offer: StateFlow<Int?> = offerState.asStateFlow()

    private val announceState = MutableStateFlow<Int?>(null)

    /** The charge at which «Авто» has just turned the mode on by itself, while that capsule is up. */
    val announce: StateFlow<Int?> = announceState.asStateFlow()

    val level: StateFlow<Int> = battery.map { it.level }.stateIn(scope, SharingStarted.Eagerly, battery.value.level)

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                battery.value = readBattery(app, intent.takeIf { it.action == Intent.ACTION_BATTERY_CHANGED })
            }
        }
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED).apply { addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED) }
        ContextCompat.registerReceiver(app, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        combine(battery, economy) { battery, economy -> follow(battery, economy) }.launchIn(scope)
        autoOn.onEach { on ->
            val economy = economy.value
            if (on && economy.mode == EconomyMode.Auto && !announced) {
                announced = true
                announceState.value = battery.value.level
            }
        }.launchIn(scope)
    }

    private fun follow(battery: Battery, economy: Economy) {
        if (battery.charging) {
            // A new discharge may offer and announce again.
            offered = false
            announced = false
            offerState.value = null
        }
        val low = battery.level < economy.threshold
        autoOn.value = autoAfter(battery, economy.threshold, autoOn.value)
        val offering = economy.mode == EconomyMode.Off && economy.offer
        val draining = low && !battery.charging
        if (offering && draining && !offered) {
            offered = true
            offerState.value = battery.level
        }
        if (economy.mode != EconomyMode.Off || !economy.offer) offerState.value = null
    }

    /** «Включить» in the offer: «Авто», so the charger turns it off again. */
    fun acceptOffer() {
        offerState.value = null
        announced = true
        scope.launch { store.update { it.copy(mode = EconomyMode.Auto) } }
    }

    fun dismissOffer() {
        offerState.value = null
    }

    fun dismissAnnounce() {
        announceState.value = null
    }

    fun update(transform: (Economy) -> Economy) {
        scope.launch { store.update(transform) }
    }

    internal companion object {
        private const val HYSTERESIS = 5

        /** Whether «Авто» is on after [battery] changed, having been [was] before. */
        fun autoAfter(battery: Battery, threshold: Int, was: Boolean) = when {
            battery.charging -> false
            battery.saver || battery.level < threshold -> true
            battery.level >= threshold + HYSTERESIS -> false
            else -> was
        }

        private const val FULL = 100

        private fun readBattery(context: Context, changed: Intent? = null): Battery {
            val intent = changed ?: ContextCompat.registerReceiver(
                context,
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            val percent = if (level >= 0 && scale > 0) level * FULL / scale else FULL
            val saver = context.getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
            return Battery(percent, charging, saver)
        }
    }
}
