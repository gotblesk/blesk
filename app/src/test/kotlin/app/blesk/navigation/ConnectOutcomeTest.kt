package app.blesk.navigation

import app.blesk.core.messaging.RegisterResult
import app.blesk.core.messaging.ServerTrouble
import app.blesk.feature.settings.ConnectOutcome
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectOutcomeTest {
    @Test
    fun everyTroubleWithTheServerKeepsItsOwnText() {
        val troubles = listOf(
            ServerTrouble.TooManyAttempts(120_000),
            ServerTrouble.TooManyAttempts(null),
            ServerTrouble.CertificateMismatch,
            ServerTrouble.CertificateUntrusted,
            ServerTrouble.AppTooOld,
            ServerTrouble.ServerTooOld,
            ServerTrouble.NotBlesk,
            ServerTrouble.Unreachable,
        )
        val outcomes = listOf(
            ConnectOutcome.TooManyAttempts,
            ConnectOutcome.TooManyAttempts,
            ConnectOutcome.CertificateMismatch,
            ConnectOutcome.CertificateUntrusted,
            ConnectOutcome.AppTooOld,
            ConnectOutcome.ServerTooOld,
            ConnectOutcome.NotBlesk,
            ConnectOutcome.Unreachable,
        )
        assertEquals(outcomes, troubles.map(RegisterResult::outcome))
    }
}
