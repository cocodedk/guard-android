package dk.cocode.guard.net

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class IcmpUnreachableTest {
    private val src4 = addr("10.111.1.1")
    private val dst4 = addr("203.0.113.7")
    private val src6 = addr("2001:db8::1")
    private val dst6 = addr("2001:db8::2")

    private fun reply(buf: ByteArray) = icmpUnreachableFor(buf, parseIpPacket(buf, buf.size)!!)

    @Test
    fun ipv4Code13() {
        val original = v4Packet(PROTOCOL_UDP, src4, dst4, udpDatagram(40000, 9999, ByteArray(30) { it.toByte() }))
        val r = reply(original)
        assertEquals(20 + 8 + 28, r.size)
        assertArrayEquals(dst4, r.copyOfRange(12, 16)) // from the blocked address
        assertArrayEquals(src4, r.copyOfRange(16, 20)) // to the sender
        assertEquals(PROTOCOL_ICMP, r.u8(9))
        assertEquals(3, r.u8(20))
        assertEquals(13, r.u8(21))
        assertArrayEquals(original.copyOfRange(0, 28), r.copyOfRange(28, 56)) // header + 8 bytes
        assertEquals(0xFFFF, onesSum(r, 0, 20))
        assertEquals(0xFFFF, onesSum(r, 20, r.size))
    }

    @Test
    fun ipv4WithOptionsQuotesTheWholeHeader() {
        val original = v4Packet(PROTOCOL_UDP, src4, dst4, udpDatagram(40000, 9999, ByteArray(30)), optionWords = 3)
        val r = reply(original)
        assertEquals(20 + 8 + 32 + 8, r.size)
        assertEquals(13, r.u8(21))
        assertArrayEquals(original.copyOfRange(0, 40), r.copyOfRange(28, 68))
        assertEquals(0xFFFF, onesSum(r, 20, r.size))
    }

    @Test
    fun ipv6Code1() {
        val original = v6Packet(PROTOCOL_UDP, src6, dst6, udpDatagram(40000, 9999, ByteArray(30)))
        val r = reply(original)
        assertEquals(40 + 8 + original.size, r.size)
        assertArrayEquals(dst6, r.copyOfRange(8, 24))
        assertArrayEquals(src6, r.copyOfRange(24, 40))
        assertEquals(PROTOCOL_ICMPV6, r.u8(6))
        assertEquals(1, r.u8(40))
        assertEquals(1, r.u8(41))
        assertArrayEquals(original, r.copyOfRange(48, r.size))
        assertEquals(0xFFFF, onesSum(r, 40, r.size, onesSum(r, 8, 40) + PROTOCOL_ICMPV6 + (r.size - 40)))
    }

    @Test
    fun ipv6ReplyFitsIn1280() {
        val original = v6Packet(PROTOCOL_UDP, src6, dst6, udpDatagram(40000, 9999, ByteArray(1500 - 48)))
        assertEquals(1500, original.size)
        val r = reply(original)
        assertEquals(1280, r.size)
        assertEquals(0xFFFF, onesSum(r, 40, r.size, onesSum(r, 8, 40) + PROTOCOL_ICMPV6 + (r.size - 40)))
    }
}
