package dk.cocode.guard.ui

import dk.cocode.guard.R
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import dk.cocode.guard.vpn.StopReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeUiTest {
    private fun ui(
        status: ProtectionStatus,
        strict: Boolean = false,
        alwaysOn: Boolean = false,
        notifications: Boolean = true,
    ) = homeUi(ProtectionState(status = status, privateDnsStrict = strict, alwaysOn = alwaysOn), notifications)

    @Test
    fun off() {
        val ui = ui(ProtectionStatus.Off)
        assertEquals(R.string.status_off, ui.statusText)
        assertEquals(Tone.Notice, ui.statusTone)
        assertEquals(R.string.detail_off, ui.detailText)
        assertEquals(emptyList<HomeCard>(), ui.cards)
        assertEquals(HomeAction.Start, ui.primaryAction)
        assertFalse(ui.showCounter)
    }

    @Test
    fun permissionRefused() {
        val ui = ui(ProtectionStatus.PermissionRefused)
        assertEquals(R.string.status_off, ui.statusText)
        assertEquals(Tone.Notice, ui.statusTone)
        assertNull(ui.detailText)
        assertEquals(listOf(HomeCard.PermissionRefused), ui.cards)
        assertEquals(HomeAction.TryAgain, ui.primaryAction)
        assertFalse(ui.showCounter)
    }

    @Test
    fun starting() {
        val ui = ui(ProtectionStatus.Starting)
        assertEquals(R.string.status_starting, ui.statusText)
        assertEquals(Tone.Notice, ui.statusTone)
        assertNull(ui.detailText)
        assertEquals(emptyList<HomeCard>(), ui.cards)
        assertEquals(HomeAction.Starting, ui.primaryAction)
        assertFalse(ui.showCounter)
    }

    @Test
    fun protectedWords() {
        val ui = ui(ProtectionStatus.Protected)
        assertEquals(R.string.status_protected, ui.statusText)
        assertEquals(Tone.Ok, ui.statusTone)
        assertEquals(R.string.detail_protected, ui.detailText)
        assertEquals(emptyList<HomeCard>(), ui.cards)
        assertEquals(HomeAction.Stop, ui.primaryAction)
        assertTrue(ui.showCounter)
    }

    @Test
    fun protectedWithStrictPrivateDns() {
        val ui = ui(ProtectionStatus.Protected, strict = true, alwaysOn = true, notifications = false)
        assertEquals(R.string.status_bypassed, ui.statusText)
        assertEquals(Tone.Urgent, ui.statusTone)
        assertNull(ui.detailText)
        assertEquals(listOf(HomeCard.PrivateDns, HomeCard.AlwaysOn, HomeCard.NotificationsOff), ui.cards)
        assertEquals(HomeAction.None, ui.primaryAction)
        assertTrue(ui.showCounter)
    }

    private fun assertStopped(reason: StopReason, card: HomeCard) {
        val ui = ui(ProtectionStatus.Stopped(reason))
        assertEquals(R.string.status_off, ui.statusText)
        assertEquals(Tone.Urgent, ui.statusTone)
        assertNull(ui.detailText)
        assertEquals(listOf(card), ui.cards)
        assertEquals(HomeAction.StartAgain, ui.primaryAction)
        assertFalse(ui.showCounter)
    }

    @Test
    fun stoppedRevoked() = assertStopped(StopReason.Revoked, HomeCard.StoppedRevoked)

    @Test
    fun stoppedLockdown() = assertStopped(StopReason.Lockdown, HomeCard.StoppedLockdown)

    @Test
    fun stoppedError() = assertStopped(StopReason.Error, HomeCard.StoppedError)

    @Test
    fun privateDnsCardOnlyWhenProtectedAndStrict() {
        val others = listOf(ProtectionStatus.Off, ProtectionStatus.Starting, ProtectionStatus.PermissionRefused) +
            StopReason.entries.map { ProtectionStatus.Stopped(it) }
        for (status in others) assertFalse(HomeCard.PrivateDns in ui(status, strict = true).cards)
        assertFalse(HomeCard.PrivateDns in ui(ProtectionStatus.Protected, strict = false).cards)
        assertTrue(HomeCard.PrivateDns in ui(ProtectionStatus.Protected, strict = true).cards)
    }

    @Test
    fun bypassedNeverSaysProtected() {
        val ui = ui(ProtectionStatus.Protected, strict = true)
        assertNotEquals(R.string.status_protected, ui.statusText)
        assertNotEquals(R.string.detail_protected, ui.detailText)
    }

    @Test
    fun alwaysOnHidesStopAndShowsCard() {
        val ui = ui(ProtectionStatus.Protected, alwaysOn = true)
        assertEquals(HomeAction.None, ui.primaryAction)
        assertEquals(listOf(HomeCard.AlwaysOn), ui.cards)
    }

    @Test
    fun notificationsCardWhenRefused() {
        assertEquals(listOf(HomeCard.NotificationsOff), ui(ProtectionStatus.Off, notifications = false).cards)
        assertEquals(listOf(HomeCard.NotificationsOff), ui(ProtectionStatus.Protected, notifications = false).cards)
        assertEquals(emptyList<HomeCard>(), ui(ProtectionStatus.Off, notifications = true).cards)
    }
}
