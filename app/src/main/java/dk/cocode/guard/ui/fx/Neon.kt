package dk.cocode.guard.ui.fx

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text as spokenText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dk.cocode.guard.ui.theme.Condensed
import dk.cocode.guard.ui.theme.GuardColors
import dk.cocode.guard.ui.theme.NeonCut

/** A panel: Panel fill, a 1.dp border in [accent] at 55%, and a glow in [accent] (colored shadows work from API 28). */
fun Modifier.neonPanel(accent: Color, shape: Shape = NeonCut): Modifier = this
    .shadow(12.dp, shape, ambientColor = accent, spotColor = accent)
    .background(GuardColors.Panel, shape)
    .border(1.dp, accent.copy(alpha = 0.55f), shape)

/**
 * Condensed, spaced, UPPER CASE text. The capitals are only drawn: TalkBack gets [text] as written,
 * so it never spells capitals letter by letter.
 */
@Composable
fun CondensedText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    spacing: TextUnit = 1.sp,
) = Text(
    text = text.uppercase(),
    // Spoken text, not a contentDescription: it still merges with its siblings into one card stop.
    modifier = modifier.clearAndSetSemantics { spokenText = AnnotatedString(text) },
    color = color,
    style = style.copy(fontFamily = Condensed, letterSpacing = spacing),
)
