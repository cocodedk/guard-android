package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color

// The Cocode Guard palette in its neon-noir form (spec 05). ContrastTest holds every text pair to
// WCAG AA. Night and Ok are repeated in res/values/colors.xml for the window background and the icon.
object GuardColors {
    val Night = Color(0xFF070B14)
    val NightTop = Color(0xFF0A0F1E) // top of the background gradient, never behind text on its own
    val Panel = Color(0xFF0E1424)
    val OnNight = Color(0xFFEEF3FF)
    val OnNightQuiet = Color(0xFFA9B4CC)
    val Ok = Color(0xFF35C47C)
    val Notice = Color(0xFFF0A43A)
    val Urgent = Color(0xFFF07478)

    // Neon accents: borders, glows and the occasional label.
    val Cyan = Color(0xFF24E5F2)
    val Magenta = Color(0xFFFF3D8B)
    val Amber = Color(0xFFFFB347)
}
