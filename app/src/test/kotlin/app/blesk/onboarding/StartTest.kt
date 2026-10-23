package app.blesk.onboarding

import app.blesk.core.data.settings.SetupStep
import app.blesk.core.data.settings.StartState
import app.blesk.navigation.HomeKey
import org.junit.Assert.assertEquals
import org.junit.Test

class StartTest {
    private fun steps(pinSet: Boolean) = generateSequence(SetupStep.Profile) { it.next(pinSet) }.toList()

    @Test
    fun theStepsGoInTheBriefsOrder() {
        assertEquals(listOf(SetupStep.Profile, SetupStep.Phrase, SetupStep.Code, SetupStep.Theme, SetupStep.Glint), steps(pinSet = false))
    }

    @Test
    fun aCodeAlreadySetSkipsItsOffer() {
        assertEquals(SetupStep.Theme, SetupStep.Phrase.next(pinSet = true))
        assertEquals(listOf(SetupStep.Profile, SetupStep.Phrase, SetupStep.Theme, SetupStep.Glint), steps(pinSet = true))
    }

    @Test
    fun aFreshInstallStartsAtTheWelcomeScreen() {
        assertEquals(WelcomeKey, startKey(StartState.Welcome))
    }

    @Test
    fun aChosenDemoStartsInTheChats() {
        assertEquals(HomeKey, startKey(StartState.Demo))
    }

    @Test
    fun anAccountStartsInTheChats() {
        assertEquals(HomeKey, startKey(StartState.Account(pendingSetup = null)))
    }

    @Test
    fun anUnfinishedSetupResumesAtItsStep() {
        assertEquals(SetupKey(SetupStep.Code), startKey(StartState.Account(SetupStep.Code)))
    }
}
