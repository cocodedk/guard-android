package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The Cocode Guard palette, taken from the box's site (network-defence/website/styles.css)
// so the phone and the box read as one product. ContrastTest holds every pair to WCAG AA.
object GuardColors {
    val Night = Color(0xFF0F1E36)
    val Panel = Color(0xFF182A47)
    val OnNight = Color(0xFFE8EEF6)
    val OnNightQuiet = Color(0xFFA3B3CA)
    val Ok = Color(0xFF35C47C)
    val Notice = Color(0xFFF0A43A)
    // The box site's red (#E5484D) is 4.3:1 on Night, too dark for text; this lighter one is 5.9:1.
    val Urgent = Color(0xFFF07478)
}

private fun channel(c: Float): Double =
    if (c <= 0.03928f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

/** WCAG 2.x relative luminance. */
fun luminance(color: Color): Double =
    0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

/** WCAG 2.x contrast ratio, from 1.0 (none) to 21.0 (black on white). */
fun contrastRatio(a: Color, b: Color): Double {
    val la = luminance(a)
    val lb = luminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}
