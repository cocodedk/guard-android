package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color

// The Cocode Guard palette, taken from the box's site (network-defence/website/styles.css)
// so the phone and the box read as one product. ContrastTest holds every pair to WCAG AA.
// Night and Ok are repeated in res/values/colors.xml for the window background and the icon.
object GuardColors {
    val Night = Color(0xFF0F1E36)
    val Panel = Color(0xFF172B4C)
    val OnNight = Color(0xFFE8EEF6)
    val OnNightQuiet = Color(0xFFA3B3CA)
    val Ok = Color(0xFF35C47C)
    val Notice = Color(0xFFF0A43A)
    // The box site's red (#E5484D) is 4.3:1 on Night, too dark for text; this lighter one is 5.9:1.
    val Urgent = Color(0xFFF07478)
}
