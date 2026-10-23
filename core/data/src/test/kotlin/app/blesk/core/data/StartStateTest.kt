package app.blesk.core.data

import app.blesk.core.data.settings.AppMode
import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.core.data.settings.startStateOf
import org.junit.Assert.assertEquals
import org.junit.Test

class StartStateTest {
    private val server = AppMode.Server(url = "https://api.blesk.su", pin = null, username = "anya")

    @Test
    fun aFreshInstallWelcomes() {
        assertEquals(StartState.Welcome, startStateOf(AppMode.Demo, choseDemo = false, step = null))
    }

    @Test
    fun theDemoOnceChosenStays() {
        assertEquals(StartState.Demo, startStateOf(AppMode.Demo, choseDemo = true, step = null))
    }

    @Test
    fun anAccountOpensHome() {
        assertEquals(StartState.Account(null), startStateOf(server, choseDemo = false, step = null))
        assertEquals(StartState.Account(null), startStateOf(server, choseDemo = true, step = null))
    }

    @Test
    fun anUnfinishedSetupResumes() {
        assertEquals(StartState.Account(SetupStep.Code), startStateOf(server, choseDemo = true, step = SetupStep.Code))
    }

    @Test
    fun aSetupStepWithoutAnAccountIsIgnored() {
        assertEquals(StartState.Welcome, startStateOf(AppMode.Demo, choseDemo = false, step = SetupStep.Profile))
        assertEquals(StartState.Demo, startStateOf(AppMode.Demo, choseDemo = true, step = SetupStep.Glint))
    }
}
