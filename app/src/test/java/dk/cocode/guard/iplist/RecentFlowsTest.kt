package dk.cocode.guard.iplist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFlowsTest {
    @Test
    fun firstTimeOnly() {
        val flows = RecentFlows(4)
        assertTrue(flows.firstTime("a"))
        assertFalse(flows.firstTime("a"))
        assertTrue(flows.firstTime("b"))
    }

    @Test
    fun forgetsOldestPastCapacity() {
        val flows = RecentFlows(2)
        flows.firstTime("a")
        flows.firstTime("b")
        flows.firstTime("c") // forgets "a"
        assertFalse(flows.firstTime("b"))
        assertFalse(flows.firstTime("c"))
        assertTrue(flows.firstTime("a"))
    }
}
