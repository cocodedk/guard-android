package dk.cocode.guard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.R
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.fx.NeonScreen
import dk.cocode.guard.ui.theme.GuardColors

private val links = listOf(
    AboutLink.Website to R.string.link_website,
    AboutLink.Privacy to R.string.link_privacy,
    AboutLink.Source to R.string.link_source,
    AboutLink.Issues to R.string.link_issues,
)

private val credits = listOf(
    R.string.credit_adguard,
    R.string.credit_spamhaus,
    R.string.credit_feodo,
    R.string.credit_androidx,
)

@Composable
private fun SectionTitle(@StringRes title: Int) = CondensedText(
    text = stringResource(title),
    style = MaterialTheme.typography.titleMedium,
    color = GuardColors.Cyan,
    spacing = 3.sp,
    modifier = Modifier.padding(top = 24.dp).semantics { heading() },
)

@Composable
private fun Body(@StringRes text: Int) = Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)

/**
 * The About page. [openLink] returns false when no app can open the link, and the page then says so
 * under the links instead of failing silently.
 */
@Composable
fun AboutScreen(version: String, openLink: (AboutLink) -> Boolean, onBack: () -> Unit) {
    var noBrowser by rememberSaveable { mutableStateOf(false) }
    val open = { link: AboutLink -> noBrowser = !openLink(link) }
    NeonScreen {
        TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
            CondensedText(
                stringResource(R.string.action_back),
                style = MaterialTheme.typography.titleMedium,
                color = GuardColors.Cyan,
            )
        }
        CondensedText(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Text(stringResource(R.string.about_version, version), style = MaterialTheme.typography.bodyLarge)
        Body(R.string.about_what)
        SectionTitle(R.string.about_vpn_title)
        Body(R.string.about_vpn_body)
        SectionTitle(R.string.about_free_title)
        Body(R.string.about_free_body)
        SectionTitle(R.string.about_links_title)
        links.forEach { (link, label) -> OutlinedAction(label, { open(link) }) }
        if (noBrowser) Body(R.string.link_no_browser)
        SectionTitle(R.string.about_credits_title)
        credits.forEach {
            Text(
                text = stringResource(it),
                style = MaterialTheme.typography.bodyMedium,
                color = GuardColors.OnNightQuiet,
            )
        }
        SectionTitle(R.string.about_made_by_title)
        Body(R.string.about_made_by)
        OutlinedAction(R.string.link_contact, { open(AboutLink.Contact) })
    }
}
