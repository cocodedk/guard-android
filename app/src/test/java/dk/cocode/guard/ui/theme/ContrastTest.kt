package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import java.io.File
import kotlin.math.max
import kotlin.math.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Low-vision readers depend on contrast. Every pair is held to WCAG AA for body text (4.5:1),
// which also covers icons and large text (3:1). A palette change that breaks one fails the gate.
class ContrastTest {

    /** WCAG 2.x contrast ratio, from 1.0 (none) to 21.0 (black on white). */
    private fun contrastRatio(a: Color, b: Color): Float {
        val la = a.luminance()
        val lb = b.luminance()
        return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
    }

    private fun assertReadable(name: String, fg: Color, bg: Color) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$name is $ratio:1, needs 4.5:1", ratio >= 4.5f)
    }

    @Test
    fun textColorsReadOnBothBackgrounds() {
        // State words are drawn in Ok, Notice and Urgent, so they must pass as body text.
        val text = mapOf(
            "OnNight" to GuardColors.OnNight, "OnNightQuiet" to GuardColors.OnNightQuiet,
            "Ok" to GuardColors.Ok, "Notice" to GuardColors.Notice, "Urgent" to GuardColors.Urgent,
        )
        val backgrounds = mapOf("Night" to GuardColors.Night, "Panel" to GuardColors.Panel)
        for ((fgName, fg) in text) for ((bgName, bg) in backgrounds) {
            assertReadable("$fgName on $bgName", fg, bg)
        }
    }

    @Test
    fun everySchemeRolePairMeetsAA() {
        val s = GuardScheme
        assertReadable("onPrimary", s.onPrimary, s.primary)
        assertReadable("onSecondary", s.onSecondary, s.secondary)
        assertReadable("onError", s.onError, s.error)
        assertReadable("onBackground", s.onBackground, s.background)
        assertReadable("onSurface", s.onSurface, s.surface)
        assertReadable("onSurfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
    }

    @Test
    fun xmlColorsMatchThePalette() {
        // The window background and launcher icon can't read Kotlin, so colors.xml repeats two
        // tokens. Unit tests run from the module directory.
        val xml = File("src/main/res/values/colors.xml").readText()
        fun xmlColor(name: String): Color {
            val match = checkNotNull(Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""").find(xml)) {
                "colors.xml has no color named $name"
            }
            return Color(0xFF000000 or match.groupValues[1].toLong(16))
        }
        assertEquals(GuardColors.Night, xmlColor("night"))
        assertEquals(GuardColors.Ok, xmlColor("ok"))
    }
}
