package dk.cocode.guard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.R
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.fx.neonPanel
import dk.cocode.guard.ui.theme.GuardColors

private class CardText(@StringRes val title: Int, @StringRes val body: Int, @StringRes val action: Int? = null)

private fun textOf(card: HomeCard) = when (card) {
    HomeCard.PermissionRefused -> CardText(R.string.card_refused_title, R.string.card_refused_body)
    HomeCard.StoppedRevoked -> CardText(R.string.card_revoked_title, R.string.card_revoked_body)
    HomeCard.StoppedLockdown ->
        CardText(R.string.card_lockdown_title, R.string.card_lockdown_body, R.string.card_vpn_settings_action)
    HomeCard.StoppedError -> CardText(R.string.card_error_title, R.string.card_error_body)
    HomeCard.StoppedPrivateDns ->
        CardText(R.string.card_private_dns_title, R.string.card_private_dns_body, R.string.card_private_dns_action)
    HomeCard.AlwaysOn ->
        CardText(R.string.card_always_on_title, R.string.card_always_on_body, R.string.card_vpn_settings_action)
    HomeCard.NotificationsOff ->
        CardText(R.string.card_notifications_title, R.string.card_notifications_body, R.string.card_notifications_action)
}

/** The border and glow say what the card means; its words say it too. */
private fun accentOf(card: HomeCard): Color = when (card) {
    HomeCard.NotificationsOff -> GuardColors.Notice
    HomeCard.AlwaysOn -> GuardColors.Cyan
    else -> GuardColors.Urgent
}

// Title and body read as one TalkBack stop; the button, if any, is its own stop with its own label.
@Composable
fun HomeCardView(card: HomeCard, onAction: () -> Unit) {
    val text = textOf(card)
    Column(
        modifier = Modifier.fillMaxWidth().neonPanel(accentOf(card)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CondensedText(
                text = stringResource(text.title),
                style = MaterialTheme.typography.titleMedium,
                spacing = 1.sp,
                modifier = Modifier.semantics { heading() },
            )
            Text(text = stringResource(text.body), style = MaterialTheme.typography.bodyMedium)
        }
        text.action?.let { OutlinedAction(it, onAction) }
    }
}
