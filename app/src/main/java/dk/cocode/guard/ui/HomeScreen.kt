package dk.cocode.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dk.cocode.guard.R
import dk.cocode.guard.ui.theme.GuardColors
import dk.cocode.guard.vpn.ProtectionState
import dk.cocode.guard.vpn.ProtectionStatus
import java.text.NumberFormat

private fun Tone.color(): Color = when (this) {
    Tone.Ok -> GuardColors.Ok
    Tone.Notice -> GuardColors.Notice
    Tone.Urgent -> GuardColors.Urgent
}

private fun statusIcon(state: ProtectionState, ui: HomeUi): Int = when (state.status) {
    ProtectionStatus.Protected -> if (ui.statusTone == Tone.Ok) R.drawable.ic_shield_on else R.drawable.ic_warning
    is ProtectionStatus.Stopped -> R.drawable.ic_shield_stopped
    else -> R.drawable.ic_shield_off
}

private fun actionLabel(action: HomeAction): Int? = when (action) {
    HomeAction.Start -> R.string.action_start
    HomeAction.Starting -> R.string.action_starting
    HomeAction.Stop -> R.string.action_stop
    HomeAction.StartAgain -> R.string.action_start_again
    HomeAction.TryAgain -> R.string.action_try_again
    HomeAction.None -> null
}

// Built for TalkBack first: the title is a heading, the status is a polite live region so a change
// is spoken, and the column scrolls so text at 200% font size is never clipped.
@Composable
fun HomeScreen(
    state: ProtectionState,
    notificationsAllowed: Boolean,
    onAction: (HomeAction) -> Unit,
    onCardAction: (HomeCard) -> Unit,
) {
    val ui = homeUi(state, notificationsAllowed)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.semantics { heading() },
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    painter = painterResource(statusIcon(state, ui)),
                    contentDescription = null, // decorative: the words say the same
                    tint = ui.statusTone.color(),
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = stringResource(ui.statusText),
                    style = MaterialTheme.typography.titleLarge,
                    color = ui.statusTone.color(),
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            ui.detailText?.let { Text(text = stringResource(it), style = MaterialTheme.typography.bodyLarge) }
            ui.cards.forEach { card -> HomeCardView(card) { onCardAction(card) } }
            if (ui.showCounter) {
                // Not a live region: it would talk over everything else.
                Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                    Text(
                        text = stringResource(R.string.counter, state.blockedCount),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.list_line, NumberFormat.getIntegerInstance().format(state.listSize)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            actionLabel(ui.primaryAction)?.let { label ->
                Button(
                    onClick = { onAction(ui.primaryAction) },
                    enabled = ui.primaryAction != HomeAction.Starting,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(
                text = stringResource(R.string.closing_line),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
