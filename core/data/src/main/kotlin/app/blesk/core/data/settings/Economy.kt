package app.blesk.core.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.economyDataStore: DataStore<Preferences> by preferencesDataStore(name = "economy")

/** «Экономный режим»: never, by itself on low charge or system power saving, or always. */
enum class EconomyMode { Off, Auto, Always }

/**
 * What the economy mode is set to and what it does once on. Each simplification is its own
 * switch, all on by default. Delivery of messages and calls is never part of it.
 */
data class Economy(
    val mode: EconomyMode = EconomyMode.Off,
    /** «Авто» turns on below this charge, in percent. */
    val threshold: Int = DEFAULT_THRESHOLD,
    /** Offer to turn it on once per discharge when the charge drops below [threshold]. */
    val offer: Boolean = true,
    val matteGlass: Boolean = true,
    /** Moving light, tilt light and the light over skeletons stand still; glints fade out. */
    val stillLight: Boolean = true,
    /** Shorter, plainer animations and geometric metaball bridges. */
    val simpleMotion: Boolean = true,
    /** Round videos, stickers and videos play only when tapped. */
    val tapToPlay: Boolean = true,
    val wifiOnlyMedia: Boolean = true,
    val cap60Hz: Boolean = true,
) {
    companion object {
        const val DEFAULT_THRESHOLD = 20
        const val MIN_THRESHOLD = 10
        const val MAX_THRESHOLD = 50
    }
}

/** What economy changes right now: nothing while it is off, its switches while it is on. */
data class EconomyEffects(
    val matteGlass: Boolean = false,
    val stillLight: Boolean = false,
    val simpleMotion: Boolean = false,
    val tapToPlay: Boolean = false,
    val wifiOnlyMedia: Boolean = false,
    val cap60Hz: Boolean = false,
) {
    companion object {
        val None = EconomyEffects()

        fun of(economy: Economy, active: Boolean) = if (!active) {
            None
        } else {
            EconomyEffects(
                matteGlass = economy.matteGlass,
                stillLight = economy.stillLight,
                simpleMotion = economy.simpleMotion,
                tapToPlay = economy.tapToPlay,
                wifiOnlyMedia = economy.wifiOnlyMedia,
                cap60Hz = economy.cap60Hz,
            )
        }
    }
}

/** Kept apart from the other settings: it is read on every battery change. */
class EconomyStore(context: Context) {
    private val store = context.applicationContext.economyDataStore

    val economy: Flow<Economy> = store.data.map(::read).distinctUntilChanged()

    suspend fun update(transform: (Economy) -> Economy) {
        store.edit { prefs ->
            val current = read(prefs)
            val next = transform(current)
            prefs[MODE] = next.mode.name
            prefs[THRESHOLD] = next.threshold.coerceIn(Economy.MIN_THRESHOLD, Economy.MAX_THRESHOLD)
            prefs[OFFER] = next.offer
            prefs[MATTE_GLASS] = next.matteGlass
            prefs[STILL_LIGHT] = next.stillLight
            prefs[SIMPLE_MOTION] = next.simpleMotion
            prefs[TAP_TO_PLAY] = next.tapToPlay
            prefs[WIFI_ONLY] = next.wifiOnlyMedia
            prefs[CAP_60] = next.cap60Hz
        }
    }

    private fun read(prefs: Preferences) = Economy(
        mode = prefs[MODE]?.let { name -> EconomyMode.entries.firstOrNull { it.name == name } } ?: EconomyMode.Off,
        threshold = (prefs[THRESHOLD] ?: Economy.DEFAULT_THRESHOLD).coerceIn(Economy.MIN_THRESHOLD, Economy.MAX_THRESHOLD),
        offer = prefs[OFFER] ?: true,
        matteGlass = prefs[MATTE_GLASS] ?: true,
        stillLight = prefs[STILL_LIGHT] ?: true,
        simpleMotion = prefs[SIMPLE_MOTION] ?: true,
        tapToPlay = prefs[TAP_TO_PLAY] ?: true,
        wifiOnlyMedia = prefs[WIFI_ONLY] ?: true,
        cap60Hz = prefs[CAP_60] ?: true,
    )

    private companion object {
        val MODE = stringPreferencesKey("mode")
        val THRESHOLD = intPreferencesKey("threshold")
        val OFFER = booleanPreferencesKey("offer")
        val MATTE_GLASS = booleanPreferencesKey("matte_glass")
        val STILL_LIGHT = booleanPreferencesKey("still_light")
        val SIMPLE_MOTION = booleanPreferencesKey("simple_motion")
        val TAP_TO_PLAY = booleanPreferencesKey("tap_to_play")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val CAP_60 = booleanPreferencesKey("cap_60")
    }
}
