package dk.cocode.guard.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.ui.fx.CondensedText
import dk.cocode.guard.ui.theme.ButtonShape
import dk.cocode.guard.ui.theme.GuardColors

// All three are full width and at least 48.dp high. The label is condensed capitals; TalkBack still
// hears it as written.
@Composable
private fun NeonButton(
    @StringRes label: Int,
    onClick: () -> Unit,
    container: Color,
    content: Color,
    border: Color?,
    glow: Color?,
    modifier: Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(containerColor = container, contentColor = content),
        border = border?.let { BorderStroke(1.dp, it) },
        modifier = modifier.fillMaxWidth().heightIn(min = 48.dp).let {
            if (glow != null && enabled) it.shadow(12.dp, ButtonShape, ambientColor = glow, spotColor = glow) else it
        },
    ) {
        CondensedText(stringResource(label), style = MaterialTheme.typography.titleMedium, spacing = 1.sp)
    }
}

/** The one bright fill on the screen: start, start again, try again. */
@Composable
fun FilledAction(@StringRes label: Int, onClick: () -> Unit, enabled: Boolean = true) = NeonButton(
    label, onClick, GuardColors.Ok, GuardColors.Night, border = null, glow = GuardColors.Ok,
    modifier = Modifier, enabled = enabled,
)

@Composable
fun StopAction(@StringRes label: Int, onClick: () -> Unit) = NeonButton(
    label, onClick, GuardColors.Magenta.copy(alpha = 0.14f), GuardColors.OnNight,
    border = GuardColors.Magenta, glow = GuardColors.Magenta, modifier = Modifier,
)

/** Every secondary button: it never competes with the one filled primary action. */
@Composable
fun OutlinedAction(@StringRes label: Int, onClick: () -> Unit, modifier: Modifier = Modifier) = NeonButton(
    label, onClick, Color.Transparent, GuardColors.Cyan, border = GuardColors.Cyan, glow = null, modifier = modifier,
)
