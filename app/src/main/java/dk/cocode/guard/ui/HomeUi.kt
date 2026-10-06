package dk.cocode.guard.ui

import dk.cocode.guard.R
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import dk.cocode.guard.vpn.StopReason

enum class Tone { Ok, Notice, Urgent }

enum class HomeCard {
    PermissionRefused, StoppedRevoked, StoppedLockdown, StoppedError, PrivateDns, AlwaysOn, NotificationsOff,
}

enum class HomeAction { Start, Starting, Stop, StartAgain, TryAgain, None }

data class HomeUi(
    val statusText: Int,
    val statusTone: Tone,
    val detailText: Int?,
    val cards: List<HomeCard>,
    val primaryAction: HomeAction,
    val showCounter: Boolean,
)

/** What the screen shows for a state: the whole journey in one pure function. */
fun homeUi(state: ProtectionState, notificationsAllowed: Boolean): HomeUi {
    val notificationsCard = listOfNotNull(HomeCard.NotificationsOff.takeUnless { notificationsAllowed })
    return when (val status = state.status) {
        ProtectionStatus.Off -> HomeUi(
            R.string.status_off, Tone.Notice, R.string.detail_off, notificationsCard, HomeAction.Start, false,
        )
        ProtectionStatus.PermissionRefused -> HomeUi(
            R.string.status_off, Tone.Notice, null, listOf(HomeCard.PermissionRefused), HomeAction.TryAgain, false,
        )
        ProtectionStatus.Starting -> HomeUi(
            R.string.status_starting, Tone.Notice, null, emptyList(), HomeAction.Starting, false,
        )
        ProtectionStatus.Protected -> {
            val bypassed = state.privateDnsStrict
            val cards = listOfNotNull(HomeCard.PrivateDns.takeIf { bypassed }, HomeCard.AlwaysOn.takeIf { state.alwaysOn }) +
                notificationsCard
            HomeUi(
                statusText = if (bypassed) R.string.status_bypassed else R.string.status_protected,
                statusTone = if (bypassed) Tone.Urgent else Tone.Ok,
                detailText = if (bypassed) null else R.string.detail_protected,
                cards = cards,
                primaryAction = if (state.alwaysOn) HomeAction.None else HomeAction.Stop,
                showCounter = true,
            )
        }
        is ProtectionStatus.Stopped -> HomeUi(
            R.string.status_off, Tone.Urgent, null, listOf(stoppedCard(status.reason)), HomeAction.StartAgain, false,
        )
    }
}

private fun stoppedCard(reason: StopReason) = when (reason) {
    StopReason.Revoked -> HomeCard.StoppedRevoked
    StopReason.Lockdown -> HomeCard.StoppedLockdown
    StopReason.Error -> HomeCard.StoppedError
}
