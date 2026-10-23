package app.blesk.onboarding

import app.blesk.core.data.settings.SetupStep
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
}
