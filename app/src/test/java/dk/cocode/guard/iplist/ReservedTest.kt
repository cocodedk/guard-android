package dk.cocode.guard.iplist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReservedTest {
    private fun reserved(text: String) = isReserved(parseCidr(text)!!)

    @Test
    fun privateRangesAreReserved() {
        val ranges = listOf(
            "0.0.0.0/8", "10.0.0.0/8", "100.64.0.0/10", "127.0.0.0/8", "169.254.0.0/16", "172.16.0.0/12",
            "192.0.0.0/24", "192.168.0.0/16", "198.18.0.0/15", "224.0.0.0/4", "240.0.0.0/4",
            "::/8", "64:ff9b::/96", "fc00::/7", "fe80::/10", "ff00::/8",
        )
        for (r in ranges) assertTrue(r, reserved(r))
        assertTrue(reserved("10.0.0.0/7")) // overlaps 10.0.0.0/8
        assertTrue(reserved("192.168.4.0/24")) // inside one
    }

    @Test
    fun defaultRoutesAreReserved() {
        assertTrue(reserved("0.0.0.0/0"))
        assertTrue(reserved("::/0"))
    }

    @Test
    fun tunnelAddressesAreReserved() {
        assertTrue(reserved("10.111.222.1/32"))
        assertTrue(reserved("10.111.222.2/32"))
        assertTrue(reserved("fd47:7561:7264::1/128"))
    }

    @Test
    fun nat64IsReserved() = assertTrue(reserved("64:ff9b::/96"))

    @Test
    fun publicRangeIsNot() {
        assertFalse(reserved("203.0.113.0/24"))
        assertFalse(reserved("2001:db8::/32"))
    }
}
