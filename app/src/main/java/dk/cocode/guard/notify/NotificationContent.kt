package dk.cocode.guard.notify

import dk.cocode.guard.R
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import dk.cocode.guard.vpn.StopReason

/** What the ongoing notification says, as string ids; pure so it is unit-tested. */
data class OngoingContent(val title: Int, val text: Int?, val showStop: Boolean, val icon: Int)

fun ongoingContent(state: ProtectionState): OngoingContent = when {
    state.status == ProtectionStatus.Starting ->
        OngoingContent(R.string.status_starting, null, false, R.drawable.ic_shield_off)
    state.privateDnsStrict -> OngoingContent(
        R.string.status_bypassed, R.string.card_private_dns_title, canStop(state), R.drawable.ic_warning,
    )
    else -> OngoingContent(
        R.string.notif_protected_title, R.string.notif_protected_text, canStop(state), R.drawable.ic_shield_on,
    )
}

// Android restarts an Always-on service, so a Stop there would do nothing.
private fun canStop(state: ProtectionState) = state.status == ProtectionStatus.Protected && !state.alwaysOn

/** The stop alert: [onlyAlertOnce] so a restart that stops again (Always-on with lockdown) stays quiet. */
data class AlertContent(val title: Int, val body: Int, val onlyAlertOnce: Boolean)

fun alertContent(reason: StopReason) = AlertContent(R.string.notif_stopped_title, alertBody(reason), true)

/** The alert's text is the same words as the matching card on the screen. */
fun alertBody(reason: StopReason): Int = when (reason) {
    StopReason.Revoked -> R.string.card_revoked_body
    StopReason.Lockdown -> R.string.card_lockdown_body
    StopReason.Error -> R.string.card_error_body
}
