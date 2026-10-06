package dk.cocode.guard.ui.theme

import android.graphics.Typeface
import androidx.compose.ui.text.font.FontFamily

// Built into Android, so no font file ships. Only composables touch this file, never a unit test:
// Typeface does not run on the JVM.
val Condensed = FontFamily(Typeface.create("sans-serif-condensed", Typeface.NORMAL))
