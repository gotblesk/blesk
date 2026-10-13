package app.blesk.economy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EconomyAutoTest {
    private fun auto(level: Int, charging: Boolean = false, saver: Boolean = false, was: Boolean = false) =
        EconomyMonitor.autoAfter(Battery(level, charging, saver), threshold = 20, was = was)

    @Test
    fun turnsOnBelowThreshold() = assertTrue(auto(19))

    @Test
    fun turnsOnWithSystemSaver() = assertTrue(auto(80, saver = true))

    @Test
    fun chargerTurnsItOff() = assertFalse(auto(10, charging = true, was = true))

    @Test
    fun staysOnUntilFivePercentAbove() {
        assertTrue(auto(22, was = true))
        assertFalse(auto(25, was = true))
    }

    @Test
    fun staysOffInTheMiddle() = assertFalse(auto(22, was = false))
}
