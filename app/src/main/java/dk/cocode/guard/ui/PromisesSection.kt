package dk.cocode.guard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dk.cocode.guard.R
import dk.cocode.guard.ui.theme.GuardColors

private val promises = listOf(
    R.string.promise_no_data,
    R.string.promise_on_phone,
    R.string.promise_only_dns,
    R.string.promise_free,
    R.string.promise_permissions,
    R.string.promise_accessible,
)

/** A full-width outlined button: it never competes with the one filled primary action. */
@Composable
fun OutlinedAction(@StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.Button },
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * "Godt at vide": plain text, no live region. Each promise is one TalkBack stop; the check icon is
 * decorative because the words carry the meaning.
 */
@Composable
fun PromisesSection(onAbout: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onBackground,
            ),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.promise_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.semantics { heading() },
                )
                promises.forEach { Promise(it) }
                Text(
                    text = stringResource(R.string.promise_limit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedAction(R.string.action_about, onAbout)
    }
}

@Composable
private fun Promise(@StringRes text: Int) {
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null, // decorative: the words say the same
            tint = GuardColors.Ok,
            // bodyLarge lines are 24sp tall; the 2dp offset centres the icon on the first line.
            modifier = Modifier.padding(top = 2.dp).size(20.dp),
        )
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyLarge)
    }
}
