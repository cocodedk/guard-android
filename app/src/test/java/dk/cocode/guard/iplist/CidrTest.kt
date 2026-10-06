package dk.cocode.guard.iplist

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CidrTest {
    private fun cidr(text: String) = parseCidr(text)!!

    private fun ip(text: String) = cidr(text + if (':' in text) "/128" else "/32").address

    @Test
    fun parsesIpv4Range() {
        val c = parseCidr("192.0.2.0/24")
        assertNotNull(c)
        assertArrayEquals(byteArrayOf(192.toByte(), 0, 2, 0), c!!.address)
        assertEquals(24, c.prefix)
    }

    @Test
    fun parsesIpv6Range() {
        val c = parseCidr("2001:db8::/32")
        assertNotNull(c)
        assertArrayEquals(byteArrayOf(0x20, 0x01, 0x0d, 0xb8.toByte()) + ByteArray(12), c!!.address)
        assertEquals(32, c.prefix)
    }

    @Test
    fun rejectsHostBits() {
        assertNull(parseCidr("192.0.2.7/24"))
        assertNull(parseCidr("2001:db8::1/32"))
    }

    @Test
    fun rejectsBadPrefix() {
        for (text in listOf("192.0.2.0/33", "2001:db8::/129", "192.0.2.0/-1", "192.0.2.0", "192.0.2.0/x")) {
            assertNull(text, parseCidr(text))
        }
    }

    @Test
    fun rejectsGarbage() {
        for (text in listOf("", "/8", "garbage/24", "300.1.1.1/8", "1.2.3/8", "1.2.3.4.5/8", "::g/8", "1::2::3/8", "1:2:3:4:5:6:7:8:9/8")) {
            assertNull(text, parseCidr(text))
        }
    }

    @Test
    fun containsMatchesInsideOnly() {
        val c = cidr("192.0.2.0/24")
        assertTrue(c.contains(ip("192.0.2.0")))
        assertTrue(c.contains(ip("192.0.2.255")))
        assertFalse(c.contains(ip("192.0.1.255")))
        assertFalse(c.contains(ip("192.0.3.0")))
        assertFalse(c.contains(ip("2001:db8::1"))) // other family
        val odd = cidr("1.10.16.0/20") // not on a byte boundary
        assertTrue(odd.contains(ip("1.10.31.255")))
        assertFalse(odd.contains(ip("1.10.32.0")))
    }

    @Test
    fun overlapsBothWays() {
        val wide = cidr("10.0.0.0/8")
        val narrow = cidr("10.1.0.0/16")
        assertTrue(wide.overlaps(narrow))
        assertTrue(narrow.overlaps(wide))
        assertFalse(wide.overlaps(cidr("11.0.0.0/8")))
        assertFalse(wide.overlaps(cidr("2001:db8::/32")))
    }

    @Test
    fun normalisedText() {
        assertEquals("2001:db8::/32", cidr("2001:0db8:0000::/32").toString())
        assertEquals("192.0.2.0/24", cidr("192.0.2.0/24").toString())
        assertEquals("::/0", cidr("::/0").toString())
        assertEquals("2001:db8:0:1:1:1:1:1/128", cidr("2001:db8:0:1:1:1:1:1/128").toString())
    }
}
