package dk.cocode.guard.ui

import androidx.annotation.StringRes
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.R
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.fx.LocalMotion
import dk.cocode.guard.ui.fx.neonPanel
import dk.cocode.guard.ui.theme.GuardColors
import dk.cocode.guard.ui.theme.NeonCutSmall
import dk.cocode.guard.vpn.ProtectionState
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date

private val ValueStyle = TextStyle(fontSize = 48.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "pnum")

private fun grouped(value: Int): String = NumberFormat.getIntegerInstance().format(value)

private fun date(epochMs: Long?): String =
    epochMs?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) }.orEmpty()

/**
 * Two stat tiles and the list panel. Not a live region: it changes with every block and would talk
 * over everything else.
 */
@Composable
fun DashboardSection(state: ProtectionState) {
    val d = dashboard(state)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val tiles = listOf<@Composable (Modifier) -> Unit>(
            {
                StatTile(
                    R.string.tile_lookups_tag, R.string.tile_lookups_label, R.string.tile_lookups_spoken,
                    d.lookups, GuardColors.Cyan, it,
                )
            },
            {
                StatTile(
                    R.string.tile_connections_tag, R.string.tile_connections_label, R.string.tile_connections_spoken,
                    d.connections, GuardColors.Magenta, it,
                )
            },
        )
        if (LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { tiles.forEach { it(Modifier.fillMaxWidth()) } }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { tiles.forEach { it(Modifier.weight(1f)) } }
        }
        ListPanel(d.rows)
        if (d.addressBlockingOff) {
            Text(
                text = stringResource(R.string.address_blocking_off),
                style = MaterialTheme.typography.bodyMedium,
                color = GuardColors.OnNightQuiet,
            )
        }
    }
}

/** One TalkBack stop that always carries the target value, never a number mid-count. */
@Composable
private fun StatTile(
    @StringRes tag: Int,
    @StringRes label: Int,
    @StringRes spoken: Int,
    value: Int,
    accent: Color,
    modifier: Modifier,
) {
    val shown = if (LocalMotion.current) {
        animateIntAsState(value, tween(600, easing = LinearOutSlowInEasing), label = "count").value
    } else {
        value
    }
    val speech = stringResource(spoken, grouped(value))
    Column(
        modifier = modifier.neonPanel(accent).padding(16.dp).clearAndSetSemantics { contentDescription = speech },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        CondensedText(stringResource(tag), style = MaterialTheme.typography.labelLarge, color = accent, spacing = 3.sp)
        Text(grouped(shown), style = ValueStyle, color = GuardColors.OnNight)
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = GuardColors.OnNightQuiet)
    }
}

@Composable
private fun ListPanel(rows: List<ListRow>) {
    Column(
        modifier = Modifier.fillMaxWidth().neonPanel(GuardColors.Amber, NeonCutSmall).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CondensedText(
            text = stringResource(R.string.lists_title),
            style = MaterialTheme.typography.titleMedium,
            color = GuardColors.Amber,
            spacing = 3.sp,
            modifier = Modifier.semantics { heading() },
        )
        rows.forEach { ListRowView(it) }
    }
}

private fun ListLook.color(): Color = when (this) {
    ListLook.Active -> GuardColors.Ok
    ListLook.Paused -> GuardColors.Notice
    ListLook.NotYet -> GuardColors.OnNightQuiet
}

/** The name, then the state in words; the marker only repeats what the words say. */
@Composable
private fun ListRowView(row: ListRow) {
    val color = row.look.color()
    val count = grouped(row.count ?: 0)
    val state = when {
        row.look == ListLook.Active && row.fetched == null -> stringResource(R.string.list_names_active, count)
        row.look == ListLook.Active -> stringResource(R.string.list_active, count, date(row.fetched))
        row.look == ListLook.Paused -> stringResource(R.string.list_paused, date(row.fetched))
        else -> stringResource(R.string.list_not_yet)
    }
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val marker = Modifier.size(10.dp).rotate(45f)
        Box(if (row.look == ListLook.NotYet) marker.border(1.dp, color) else marker.background(color))
        Text(row.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(
            text = state,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}
