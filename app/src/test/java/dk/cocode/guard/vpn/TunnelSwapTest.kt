package dk.cocode.guard.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class TunnelSwapTest {
    private val log = ArrayList<String>()

    private inner class Fd(val name: String) : AutoCloseable {
        override fun close() {
            log.add("close $name")
        }
    }

    @Test
    fun newTunnelComesUpBeforeOldCloses() {
        val result = replaceTunnel(Fd("old"), "old loop", { log.add("establish"); Fd("new") },
            { log.add("start loop on ${it.name}"); "new loop" }) { log.add("stop $it") }
        assertEquals(listOf("establish", "start loop on new", "stop old loop", "close old"), log)
        assertEquals("new loop", result!!.second)
    }

    @Test
    fun failedEstablishLeavesOldAlone() {
        val result = replaceTunnel(Fd("old"), "old loop", { null }, { "never" }) { log.add("stop $it") }
        assertNull(result)
        assertEquals(emptyList<String>(), log)
    }

    @Test
    fun failedLoopStartClosesNewTunnel() {
        try {
            replaceTunnel<Fd, String>(Fd("old"), "old loop", { Fd("new") }, { error("no thread") }) { log.add("stop $it") }
            fail("expected the start failure")
        } catch (e: IllegalStateException) {
            assertEquals(listOf("close new"), log)
        }
    }
}
