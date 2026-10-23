package app.blesk.onboarding

import app.blesk.core.messaging.ServerTrouble
import app.blesk.core.network.ServerAddress
import app.blesk.feature.onboarding.AccessProblem
import app.blesk.shared.protocol.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignUpTest {
    private val own = ServerAddress.DEFAULT
    private val other = ServerAddress("https://chat.example.ru")

    @Test
    fun everyTroubleHasItsProblem() {
        val troubles = mapOf<ServerTrouble, AccessProblem>(
            ServerTrouble.TooManyAttempts(120_000) to AccessProblem.TooManyAttempts(120_000),
            ServerTrouble.TooManyAttempts(null) to AccessProblem.TooManyAttempts(60_000),
            ServerTrouble.CertificateMismatch to AccessProblem.CertificateMismatch,
            ServerTrouble.CertificateUntrusted to AccessProblem.CertificateUntrusted(otherServer = false),
            ServerTrouble.AppTooOld to AccessProblem.AppTooOld,
            ServerTrouble.ServerTooOld to AccessProblem.ServerTooOld,
            ServerTrouble.NotBlesk to AccessProblem.NotBlesk(otherServer = false),
            ServerTrouble.Unreachable to AccessProblem.Unreachable(otherServer = false),
        )
        troubles.forEach { (trouble, problem) -> assertEquals(trouble.toString(), problem, trouble.problem(own)) }
    }

    @Test
    fun anotherServerIsToldToCheckItsAddress() {
        assertEquals(AccessProblem.Unreachable(otherServer = true), ServerTrouble.Unreachable.problem(other))
        assertEquals(AccessProblem.NotBlesk(otherServer = true), ServerTrouble.NotBlesk.problem(other))
        assertEquals(AccessProblem.CertificateUntrusted(otherServer = true), ServerTrouble.CertificateUntrusted.problem(other))
        assertEquals(AccessProblem.CertificateMismatch, ServerTrouble.CertificateMismatch.problem(other))
    }

    @Test
    fun theFirstStepFollowsTheServer() {
        val server = ServerRef.of(other)
        assertEquals(InviteKey(server), firstSignUpKey(server, ServerInfo(invites = true)))
        assertEquals(InviteKey(server), firstSignUpKey(server, ServerInfo(invites = null)))
        assertEquals(UsernameKey(server, invite = null), firstSignUpKey(server, ServerInfo(invites = false)))
    }

    @Test
    fun anInviteMayBeLeftOutOnlyWhereTheServerAllowsIt() {
        assertTrue(inviteRequired(null))
        assertTrue(inviteRequired(ServerInfo(invites = true)))
        assertFalse(inviteRequired(ServerInfo(invites = null)))
        assertFalse(inviteRequired(ServerInfo(invites = false)))
    }
}
