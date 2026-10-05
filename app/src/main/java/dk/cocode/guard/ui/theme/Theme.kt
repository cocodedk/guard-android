package dk.cocode.guard.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// One dark scheme in every system mode: the box's night palette is the product's look, and its
// contrast is what ContrastTest checks. Type stays Material's default, sized in sp, so it follows
// the owner's font-size setting.
internal val GuardScheme = darkColorScheme(
    primary = GuardColors.Ok,
    onPrimary = GuardColors.Night,
    secondary = GuardColors.Notice,
    onSecondary = GuardColors.Night,
    error = GuardColors.Urgent,
    onError = GuardColors.Night,
    background = GuardColors.Night,
    onBackground = GuardColors.OnNight,
    surface = GuardColors.Night,
    onSurface = GuardColors.OnNight,
    surfaceVariant = GuardColors.Panel,
    onSurfaceVariant = GuardColors.OnNightQuiet,
)

@Composable
fun GuardTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = GuardScheme, content = content)
}
