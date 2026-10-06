package dk.cocode.guard.ui.fx

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTest {
    @Test
    fun zeroScaleMeansNoMotion() = assertFalse(motionAllowed(0f))

    @Test
    fun normalScaleAllowsMotion() {
        assertTrue(motionAllowed(1f))
        assertTrue(motionAllowed(0.5f))
    }

    @Test
    fun rainIsGoneFromThirtyPercentDown() {
        assertEquals(0f, rainAlphaAt(300f, 1000f), 0f)
        assertEquals(0f, rainAlphaAt(900f, 1000f), 0f)
    }

    @Test
    fun noRainPixelBelowTheFadeEndWhateverTheStrokeDoes() {
        val h = 2000f
        val end = rainFadeEnd(h)
        assertEquals(600f, end, 0f)
        // a 48dp stroke (about 130px) whose upper half is above the boundary: every pixel below is clear
        for (y in generateSequence(end) { it + 5f }.takeWhile { it <= end + 130f }) {
            assertEquals(0f, rainAlphaAt(y, h), 0f)
        }
        assertTrue(rainAlphaAt(end - 1f, h) < 0.001f)
    }

    @Test
    fun rainPeaksAtTheTopAndFadesDownward() {
        assertEquals(0.15f, rainAlphaAt(0f, 1000f), 1e-6f)
        assertTrue(rainAlphaAt(100f, 1000f) > rainAlphaAt(200f, 1000f))
    }
}
