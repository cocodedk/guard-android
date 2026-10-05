package dk.cocode.guard.ui.theme

import androidx.compose.ui.graphics.Color
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
    fun ratioIsSymmetric() {
        assertEquals(contrastRatio(GuardColors.OnNight, GuardColors.Night),
            contrastRatio(GuardColors.Night, GuardColors.OnNight), 1e-9)
    }

    @Test
    fun bodyTextOnNightMeetsAA() {
        assertAtLeast("OnNight", GuardColors.OnNight, GuardColors.Night, 4.5)
        assertAtLeast("OnNightQuiet", GuardColors.OnNightQuiet, GuardColors.Night, 4.5)
        assertAtLeast("OnNight on Panel", GuardColors.OnNight, GuardColors.Panel, 4.5)
        assertAtLeast("OnNightQuiet on Panel", GuardColors.OnNightQuiet, GuardColors.Panel, 4.5)
    }

    @Test
    fun stateColorsReadAsTextOnNight() {
        // State words are drawn in these colors, so they must pass as body text, not just icons.
        assertAtLeast("Ok", GuardColors.Ok, GuardColors.Night, 4.5)
        assertAtLeast("Notice", GuardColors.Notice, GuardColors.Night, 4.5)
        assertAtLeast("Urgent", GuardColors.Urgent, GuardColors.Night, 4.5)
        assertAtLeast("Ok on Panel", GuardColors.Ok, GuardColors.Panel, 4.5)
        assertAtLeast("Notice on Panel", GuardColors.Notice, GuardColors.Panel, 4.5)
        assertAtLeast("Urgent on Panel", GuardColors.Urgent, GuardColors.Panel, 4.5)
    }

    @Test
    fun buttonLabelOnAccentMeetsAA() {
        assertAtLeast("Night on Ok", GuardColors.Night, GuardColors.Ok, 4.5)
    }
}
