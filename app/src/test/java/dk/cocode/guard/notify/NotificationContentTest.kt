package dk.cocode.guard.notify

import dk.cocode.guard.R
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import dk.cocode.guard.vpn.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {
    private val protectedState = ProtectionState(status = ProtectionStatus.Protected)

    @Test
    fun protectedSaysProtectedAndOffersStop() {
        val c = ongoingContent(protectedState)
        assertEquals(R.string.notif_protected_title, c.title)
        assertEquals(R.string.notif_protected_text, c.text)
        assertTrue(c.showStop)
    }

    @Test
    fun strictPrivateDnsNeverSaysProtected() {
        val c = ongoingContent(protectedState.copy(privateDnsStrict = true))
        assertEquals(R.string.status_bypassed, c.title)
        assertEquals(R.string.card_private_dns_title, c.text)
        assertTrue(c.showStop)
    }

    @Test
    fun alwaysOnLeavesOutStop() {
        assertFalse(ongoingContent(protectedState.copy(alwaysOn = true)).showStop)
        assertFalse(ongoingContent(protectedState.copy(alwaysOn = true, privateDnsStrict = true)).showStop)
    }

    @Test
    fun startingSaysStartingWithoutStop() {
        val c = ongoingContent(ProtectionState(status = ProtectionStatus.Starting))
        assertEquals(R.string.status_starting, c.title)
        assertNull(c.text)
        assertFalse(c.showStop)
    }

    @Test
    fun alertUsesTheCardWordsForEachReason() {
        assertEquals(R.string.card_revoked_body, alertBody(StopReason.Revoked))
        assertEquals(R.string.card_lockdown_body, alertBody(StopReason.Lockdown))
        assertEquals(R.string.card_error_body, alertBody(StopReason.Error))
    }
}
