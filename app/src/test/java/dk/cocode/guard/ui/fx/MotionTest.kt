package dk.cocode.guard.ui.fx

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
}
