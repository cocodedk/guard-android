package dk.cocode.guard.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.R
import dk.cocode.guard.recent.AppBlocks
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.fx.neonPanel
import dk.cocode.guard.ui.theme.GuardColors

/**
 * The apps with recent blocks. Not a live region: it changes with every block and would talk over
 * the screen. Each row is one TalkBack stop and plain text.
 */
@Composable
fun RecentBlocksSection(groups: List<AppBlocks>) {
    Column(
        modifier = Modifier.fillMaxWidth().neonPanel(GuardColors.Cyan).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CondensedText(
            text = stringResource(R.string.recent_title),
            style = MaterialTheme.typography.titleMedium,
            color = GuardColors.Cyan,
            spacing = 3.sp,
            modifier = Modifier.semantics { heading() },
        )
        if (groups.isEmpty()) Text(stringResource(R.string.recent_none), style = MaterialTheme.typography.bodyLarge)
        groups.forEach { group ->
            Text(
                text = stringResource(
                    R.string.recent_group,
                    group.app ?: stringResource(R.string.recent_unknown_app),
                    group.count,
                    group.latest.joinToString(", "),
                ),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            )
        }
        Text(
            text = stringResource(R.string.recent_note),
            style = MaterialTheme.typography.bodyMedium,
            color = GuardColors.OnNightQuiet,
        )
    }
}
