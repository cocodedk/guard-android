package dk.cocode.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.R
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.fx.NeonScreen
import dk.cocode.guard.ui.fx.StatusOrb
import dk.cocode.guard.ui.theme.GuardColors
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus

private fun Tone.color(): Color = when (this) {
    Tone.Ok -> GuardColors.Ok
    Tone.Notice -> GuardColors.Notice
    Tone.Urgent -> GuardColors.Urgent
}

private fun statusIcon(state: ProtectionState): Int = when (state.status) {
    ProtectionStatus.Protected -> R.drawable.ic_shield_on
    is ProtectionStatus.Stopped -> R.drawable.ic_shield_stopped
    else -> R.drawable.ic_shield_off
}

@Composable
private fun PrimaryAction(action: HomeAction, onAction: (HomeAction) -> Unit) {
    val label = when (action) {
        HomeAction.Start -> R.string.action_start
        HomeAction.Starting -> R.string.action_starting
        HomeAction.Stop -> R.string.action_stop
        HomeAction.StartAgain -> R.string.action_start_again
        HomeAction.TryAgain -> R.string.action_try_again
        HomeAction.None -> return
    }
    if (action == HomeAction.Stop) {
        StopAction(label) { onAction(action) }
    } else {
        FilledAction(label, { onAction(action) }, enabled = action != HomeAction.Starting)
    }
}

// Built for TalkBack first: the title is a heading, the status is a polite live region so a change
// is spoken, and the column scrolls so text at 200% font size is never clipped.
@Composable
fun HomeScreen(
    state: ProtectionState,
    notificationsAllowed: Boolean,
    onAction: (HomeAction) -> Unit,
    onCardAction: (HomeCard) -> Unit,
    onAbout: () -> Unit,
) {
    val ui = homeUi(state, notificationsAllowed)
    val tone = ui.statusTone.color()
    NeonScreen {
        CondensedText(
            text = stringResource(R.string.screen_title),
            style = MaterialTheme.typography.headlineMedium,
            spacing = 1.sp,
            modifier = Modifier.semantics { heading() },
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatusOrb(tone, statusIcon(state), isProtected = state.status == ProtectionStatus.Protected)
            CondensedText(
                text = stringResource(ui.statusText),
                style = MaterialTheme.typography.headlineSmall.copy(
                    shadow = Shadow(tone.copy(alpha = 0.6f), blurRadius = 16f), // a soft glow, never behind other text
                ),
                color = tone,
                spacing = 2.sp,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        ui.detailText?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodyLarge) }
        ui.cards.forEach { card -> HomeCardView(card) { onCardAction(card) } }
        if (ui.showCounter) DashboardSection(state)
        if (ui.showRecent) RecentBlocksSection(state.recentBlocks)
        PrimaryAction(ui.primaryAction, onAction)
        PromisesSection(onAbout)
    }
}
