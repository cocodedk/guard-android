package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Low-vision readers depend on these ratios (WCAG 2.2 AA): 4.5:1 for body text,
// 3:1 for large text and icons. A palette change that breaks one fails the gate.
class ContrastTest {

    private fun assertAtLeast(name: String, fg: Color, bg: Color, min: Double) {
        val ratio = contrastRatio(fg, bg)
        assertTrue("$name is $ratio:1, needs $min:1", ratio >= min)
    }

    @Test
    fun blackOnWhiteIsTwentyOne() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
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
            assertAtLeast("$fgName on $bgName", fg, bg, 4.5)
        }
    }

    @Test
    fun everySchemeRolePairMeetsAA() {
        val s = GuardScheme
        assertAtLeast("onPrimary", s.onPrimary, s.primary, 4.5)
        assertAtLeast("onSecondary", s.onSecondary, s.secondary, 4.5)
        assertAtLeast("onError", s.onError, s.error, 4.5)
        assertAtLeast("onBackground", s.onBackground, s.background, 4.5)
        assertAtLeast("onSurface", s.onSurface, s.surface, 4.5)
        assertAtLeast("onSurfaceVariant", s.onSurfaceVariant, s.surfaceVariant, 4.5)
    }

    @Test
    fun xmlColorsMatchThePalette() {
        // The window background and launcher icon can't read Kotlin, so colors.xml repeats two
        // tokens. Unit tests run from the module directory.
        val xml = File("src/main/res/values/colors.xml").readText()
        fun xmlColor(name: String): Color {
            val hex = Regex("""<color name="$name">#([0-9A-Fa-f]{6})</color>""").find(xml)!!
                .groupValues[1]
            return Color(0xFF000000 or hex.toLong(16))
        }
        assertEquals(GuardColors.Night, xmlColor("night"))
        assertEquals(GuardColors.Ok, xmlColor("ok"))
    }
}
